package neurosurf4s.surface

class SurfaceModuleSuite extends munit.FunSuite:

  test("module metadata is available"):
    assertEquals(SurfaceModule.name, "neurosurf4s-core")
