package neurosurf4s.surface.view.javafx

import neurosurf4s.surface.view.*

class JavaFxSurfaceScalarSuite extends munit.FunSuite:
  test("production JavaFX admission and atlas construction reject scalar fragments before allocation"):
    val plan = SurfaceScalarFixture.plan()
    assert(JavaFxSurfaceBackend.validateCapabilities(plan).isLeft)
    assert(JavaFxSurfaceProbe.compile(plan).isLeft)
