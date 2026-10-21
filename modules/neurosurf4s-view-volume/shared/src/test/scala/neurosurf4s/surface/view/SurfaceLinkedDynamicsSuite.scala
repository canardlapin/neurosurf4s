package neurosurf4s.surface.view

import spatial4s.Xyz
import reframe4s.core.WorldLink
import image4s.geometry.Affine
import image4s.geometry.D3
import intaglio.*
import neuroim4s.image.*
import neuroim4s.image.world.*
import neuroim4s.spaces.*
import neurosurf4s.surface.*

class SurfaceLinkedDynamicsSuite extends munit.FunSuite:
  private val surfaceId = SurfaceId.unsafe("left")
  private val fieldId = SurfaceTemporalFieldId.unsafe("bold")

  private def geometry(
    offset: Double = 0.0,
    faces: Seq[(Int, Int, Int)] = Seq((0, 1, 2)),
    transform: Affine[D3] = Affine.identity[D3]
  ): SurfaceGeometry =
    SurfaceGeometry(
      TriangleMesh.fromRows(
        Seq(
          Seq(offset + 0.0, 0.0, 0.0),
          Seq(offset + 1.0, 0.0, 0.0),
          Seq(offset + 0.0, 1.0, 0.0)
        ),
        faces
      ),
      Hemisphere.Left,
      SurfaceKind.Inflated,
      transform
    )

  private val axis = SurfaceTimeAxis.uniform(
    3,
    step = SurfaceSeconds.unsafe(1.0)
  ).toOption.get

  private def temporalMatrix(target: SurfaceGeometry): SurfaceMatrix[Double] =
    SurfaceMatrix.fromRows(
      target,
      Vector(VertexId(0), VertexId(1), VertexId(2)),
      Vector(
        Vector(0.0, 10.0, 20.0),
        Vector(1.0, 11.0, 21.0),
        Vector(2.0, 12.0, 22.0)
      )
    )

  test("linked surface and image selections roundtrip through world coordinates without flips"):
    val transform = testAffine(Vector(
      Vector(1.0, 0.0, 0.0, 10.0),
      Vector(0.0, 1.0, 0.0, 20.0),
      Vector(0.0, 0.0, 1.0, 30.0),
      Vector(0.0, 0.0, 0.0, 1.0)
    ))
    val target = geometry(transform = transform)
    val selection = SurfaceSelection(surfaceId, VertexId(2))
    val frame = FrameCatalog.frame(WorldSpace.declare("surface-selection-fixture").toOption.get)
    val volume = GridSpec.in(frame)(SpatialDims(64, 64, 64), Affine.identity[D3]).toOption.get
    val surface = FramedSurface.in(frame)(target, SurfacePlacement.SurfaceToWorld).toOption.get
    val link = WorldLink.shared[frame.type, frame.type, D3](frame, frame).toOption.get
    val cursor = SurfaceVolumeCursor.make(surfaceId, surface, link).toOption.get
    val inVolume = cursor.toVolume(selection).toOption.get
    val world = Xyz(inVolume.coordinates(0), inVolume.coordinates(1), inVolume.coordinates(2))
    assertEquals(world, Xyz(10.0, 21.0, 30.0))
    assertEquals(volume.voxelAt(inVolume), Right(VoxelPoint(10.0, 21.0, 30.0)))
    assertEquals(
      SurfaceWorldLink.nearestVertex(surfaceId, target, world, SurfaceLinkRadius.unsafe(0.0)),
      Right(selection)
    )
    assert(SurfaceWorldLink.nearestVertex(
      surfaceId,
      target,
      Xyz(100.0, 100.0, 100.0),
      SurfaceLinkRadius.unsafe(1.0)
    ).isLeft)

  private def testAffine(rows: Vector[Vector[Double]]): Affine[D3] =
    Affine.fromRowMajor[D3](rows.flatten).toOption.get
