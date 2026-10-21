package neurosurf4s.surface.view

import image4s.geometry.{Affine, D3, Frame, Point}
import reframe4s.core.{LinkDirection, WorldLink, WorldLinkError}
import reframe4s.lie.FramedAffine
import neuroim4s.image.{GridSpec, SpatialDims, VoxelPoint}
import neuroim4s.image.world.*
import neuroim4s.spaces.*
import neurosurf4s.surface.*

/** STP P8.02: the linked surface <-> volume cursor goes through a typed world link. */
class SurfaceVolumeLinkedCursorSuite extends munit.FunSuite:
  private def ok[E, A](result: Either[E, A]): A =
    result.fold(error => fail(s"unexpected failure: $error"), identity)

  private def affine(values: Double*): Affine[D3] = ok(Affine.fromRowMajor[D3](values.toVector))

  private def close(actual: Point[?, D3], expected: Vector[Double], tol: Double = 1e-12)(using munit.Location): Unit =
    (0 until 3).foreach(axis => assertEqualsDouble(actual.coordinates(axis), expected(axis), tol))

  private val namespace = ok(DatasetNamespace("ds-cursor"))
  private val subject = ok(SubjectId("01"))
  private val reference = ok(ReferenceAcquisition(Map("acq" -> "mprage"), ok(GeometryDigest(Vector(64, 72, 50), Vector.fill(12)(1.0), 1, 1))))
  private val tkRas = FrameCatalog.frame(WorldSpace.SubjectTkRas(namespace, subject, reference))
  private val scanner = FrameCatalog.frame(WorldSpace.SubjectNative(namespace, subject, None, reference))
  private val mni = Spaces.MNI152NLin2009cAsym

  private val mesh = TriangleMesh.fromRows(
    Vector(Vector(0.0, 0.0, 0.0), Vector(10.0, 0.0, 0.0), Vector(0.0, 10.0, 0.0), Vector(0.0, 0.0, 10.0)),
    Vector((0, 1, 2), (0, 3, 1), (0, 2, 3), (1, 3, 2))
  )
  private val geometry = SurfaceGeometry(mesh, Hemisphere.Left, SurfaceKind.White)
  private val surfaceId = SurfaceId.unsafe("lh.white")
  private val white = ok(FramedSurface.in(tkRas)(geometry, SurfacePlacement.StoredCoordinates))
  private val radius = SurfaceLinkRadius.unsafe(2.0)

  /** MNI -> tkRAS pullback (tkRAS = MNI + (5, -3, 1)); push withheld, as for a composite whose warp has no inverse. */
  private val pullOnly =
    ok(WorldLink.pullback[tkRas.type, mni.type, D3](
      FramedAffine.betweenFrames[mni.type, tkRas.type, D3](mni, tkRas)(affine(1, 0, 0, 5, 0, 1, 0, -3, 0, 0, 1, 1, 0, 0, 0, 1)),
      None,
      "fMRIPrep composite without inverse warp"
    ))

  test("guide: an MNI peak maps to subject voxels and a framed tkRAS surface through a pullback link"):
    // Synthetic fixture: the MNI -> tkRAS pullback above maps this peak to (0.5, 10.5, 0.5).
    val link = pullOnly
    val peakInMni = ok(Point.in(mni)(-4.5, 13.5, -0.5))
    val subjectVolume = ok(GridSpec.in(tkRas)(
      SpatialDims(3, 4, 3),
      affine(1, 0, 0, -0.5, 0, 1, 0, 8.5, 0, 0, 1, -0.5, 0, 0, 0, 1)
    ))
    val peakInSubject = ok(link.toLeft(peakInMni))
    val voxel = ok(subjectVolume.voxelAt(peakInSubject))
    assertEquals(voxel, VoxelPoint(1.0, 2.0, 1.0))

    val cursor = ok(SurfaceVolumeCursor.make(surfaceId, white, link))
    val hit = ok(cursor.toSurface(peakInMni, SurfaceLinkRadius.unsafe(2.0)))
    assertEquals(hit.selection, SurfaceSelection(surfaceId, VertexId(2)))
    close(hit.vertex, Vector(0.0, 10.0, 0.0))

    assertEquals(
      link.toRight(peakInSubject),
      Left(WorldLinkError.DirectionUnavailable(LinkDirection.LeftToRight, "fMRIPrep composite without inverse warp"))
    )

  test("one world links directly under an explicit alignment"):
    val scannerSurface = ok(white.toScanner(scanner, FreeSurferVolumeGeometry(Vector(64, 72, 50), affine(-1, 0, 0, 32, 0, 0, 1, -36, 0, -1, 0, 25, 0, 0, 0, 1))))
    val volumeFrame = FrameCatalog.frame(WorldSpace.SubjectNative(namespace, subject, None, reference))
    assert(!volumeFrame.sameRuntimeOwnerAs(scanner), clue = "the volume's frame is another owner of the same world")
    val cursor = ok(SurfaceVolumeCursor.make(surfaceId, scannerSurface, ok(WorldLink.shared[scanner.type, volumeFrame.type, D3](scanner, volumeFrame))))
    val selection = SurfaceSelection(surfaceId, VertexId(3))
    val inVolume = ok(cursor.toVolume(selection))
    assert(inVolume.belongsTo(volumeFrame))
    assertEquals(inVolume.coordinates, ok(scannerSurface.vertex(VertexId(3))).coordinates)
    assertEquals(ok(cursor.toSurface(inVolume, radius)).selection, selection)

