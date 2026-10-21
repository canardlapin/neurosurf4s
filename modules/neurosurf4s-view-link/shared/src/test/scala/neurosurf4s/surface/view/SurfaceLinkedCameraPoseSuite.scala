package neurosurf4s.surface.view

import reframe4s.core.WorldLink
import image4s.geometry.{Affine, D3, Point}
import reframe4s.lie.FramedAffine
import neuroim4s.image.world.*
import neuroim4s.spaces.*
import neurosurf4s.surface.*

/** STP P8.02: surface camera targets and positions are typed points in the displayed surface's frame. */
class SurfaceLinkedCameraPoseSuite extends munit.FunSuite:
  private def ok[E, A](result: Either[E, A]): A =
    result.fold(error => fail(s"unexpected failure: $error"), identity)

  private def affine(values: Double*): Affine[D3] = ok(Affine.fromRowMajor[D3](values.toVector))

  private val tkRas =
    val namespace = ok(DatasetNamespace("ds-camera"))
    val reference = ok(ReferenceAcquisition(Map("acq" -> "mprage"), ok(GeometryDigest(Vector(8, 8, 8), Vector.fill(12)(1.0), 1, 1))))
    FrameCatalog.frame(WorldSpace.SubjectTkRas(namespace, ok(SubjectId("01")), reference))
  private val mni = Spaces.MNI152NLin2009cAsym

  private val mesh = TriangleMesh.fromRows(
    Vector(Vector(0.0, 0.0, 0.0), Vector(10.0, 0.0, 0.0), Vector(0.0, 10.0, 0.0), Vector(0.0, 0.0, 10.0)),
    Vector((0, 1, 2), (0, 3, 1), (0, 2, 3), (1, 3, 2))
  )
  /** Stored coordinates placed by a translation, so display coordinates differ from stored ones. */
  private val placedGeometry = SurfaceGeometry(mesh, Hemisphere.Left, SurfaceKind.White, affine(1, 0, 0, -20, 0, 1, 0, 4, 0, 0, 1, 7, 0, 0, 0, 1))
  private val id = SurfaceId.unsafe("lh.white")
  private val model = ok(SurfaceViewerModel.make(Vector(ok(SurfaceAsset.make(id, placedGeometry))), Vector.empty))
  private val state = SurfaceViewerState.initial(model)
  private val display = ok(SurfaceDisplayFrame.declare(model, id, tkRas))

  /** Row-major 4x4 view matrix applied to a point. */
  private def toView(plan: SurfaceRenderPlan, p: Vector[Double]): Vector[Double] =
    val m = plan.camera.viewMatrix.unsafeArray
    Vector.tabulate(3)(row => m(4 * row) * p(0) + m(4 * row + 1) * p(1) + m(4 * row + 2) * p(2) + m(4 * row + 3))

  test("a volume point linked to the surface focuses the camera on its vertex"):
    val white = ok(FramedSurface.in(tkRas)(SurfaceGeometry(mesh, Hemisphere.Left, SurfaceKind.White), SurfacePlacement.StoredCoordinates))
    val shown = ok(SurfaceViewerModel.make(Vector(ok(SurfaceAsset.make(id, white.geometry))), Vector.empty))
    val shownState = SurfaceViewerState.initial(shown)
    val bound: SurfaceDisplayFrame[tkRas.type] = ok(SurfaceDisplayFrame.bind(shown, id, white))
    val pull = FramedAffine.betweenFrames[mni.type, tkRas.type, D3](mni, tkRas)(affine(1, 0, 0, 5, 0, 1, 0, -3, 0, 0, 1, 1, 0, 0, 0, 1))
    val link = ok(WorldLink.pullback[tkRas.type, mni.type, D3](pull, None, "composite without inverse"))
    val cursor = ok(SurfaceVolumeCursor.forDisplay(bound, link))
    val hit = ok(cursor.toSurface(ok(Point.in(mni)(-5.0, 3.0, 8.5)), SurfaceLinkRadius.unsafe(1.0)))
    assertEquals(hit.selection.vertex, VertexId(3))
    val initial = ok(SurfaceCompiler.cameraPose(shown, shownState, bound))
    val focused = ok(initial.focusedOn(hit.vertex))
    assertEquals(focused.target.coordinates, Vector(0.0, 0.0, 10.0))
    assertEqualsDouble(focused.distance, initial.distance, 1e-9)
    val plan = ok(SurfaceCompiler.compile(shown, shownState, bound, focused))
    val centred = toView(plan, hit.vertex.coordinates)
    assertEqualsDouble(centred(0), 0.0, 1e-4)
    assertEqualsDouble(centred(1), 0.0, 1e-4)
    assertEqualsDouble(centred(2), -focused.distance, 1e-4)

