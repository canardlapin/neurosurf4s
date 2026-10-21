package neurosurf4s.surface

import image4s.geometry.{Affine, D3, Frame}
import neuroim4s.spaces.*

class FramedSurfacePlacementSuite extends munit.FunSuite:
  private val mesh = TriangleMesh.fromRows(
    Vector(Vector(1.0, 2.0, 3.0), Vector(-4.0, 5.5, 0.25), Vector(7.0, -1.0, 2.0), Vector(0.0, 0.0, -6.0)),
    Vector((0, 1, 2), (0, 2, 3), (0, 3, 1), (1, 3, 2)))
  private val affine = Affine.fromRowMajor[D3](Vector(1, 0, 0, 10, 0, 1, 0, -20, 0, 0, 1, 30, 0, 0, 0, 1).map(_.toDouble)).toOption.get
  private val geometry = SurfaceGeometry(mesh, Hemisphere.Left, SurfaceKind.White, affine)

  test("stored and surface-to-world placement preserve distinct literal coordinates and exact topology"):
    val frame = FrameCatalog.frame(WorldSpace.declare("placement").toOption.get)
    val stored = FramedSurface.in(frame)(geometry, SurfacePlacement.StoredCoordinates).toOption.get
    val placed = FramedSurface.in(frame)(geometry, SurfacePlacement.SurfaceToWorld).toOption.get
    assertEquals(stored.vertex(VertexId(1)).toOption.get.coordinates, Vector(-4.0, 5.5, 0.25))
    assertEquals(placed.vertex(VertexId(1)).toOption.get.coordinates, Vector(6.0, -14.5, 30.25))
    assert(placed.vertex(VertexId(0)).toOption.get.belongsTo(frame))
    assert(placed.geometry.mesh.topology eq mesh.topology)
    assertEquals(placed.vertex(VertexId(4)), Left(SurfaceFrameError.VertexOutOfRange(4, 4)))

  test("runtime placement binds only through an aligned world declaration"):
    val placed = FramedSurface.inWorld(WorldSpace.Template(TemplateName("fsaverage").toOption.get))(geometry, SurfacePlacement.StoredCoordinates).toOption.get
    val bound: FramedSurface[Spaces.FsAverage] = placed.bindTo(Spaces.fsaverage).toOption.get
    assert(bound.frame.sameRuntimeOwnerAs(Spaces.fsaverage))
    assertEquals(bound.coordinates.toVector, mesh.coordinates.toVector)
    assert(bound.geometry.mesh.topology eq mesh.topology)
    assert(placed.bindTo(Spaces.fsLR).isLeft)

  test("explicit metre frames retain ownership and do not silently convert raw coordinates"):
    val frame = Frame.named[D3]("metres", spatial4s.CoordinateUnit.Meter).toOption.get
    val placed = FramedSurface.in(frame)(geometry, SurfacePlacement.StoredCoordinates).toOption.get
    assertEquals(placed.frame.unit, spatial4s.CoordinateUnit.Meter)
    assertEquals(placed.vertex(VertexId(0)).toOption.get.coordinates, Vector(1.0, 2.0, 3.0))
    assert(placed.vertex(VertexId(0)).toOption.get.belongsTo(frame))

  test("different static frames cannot be substituted and placement overflow is refused"):
    val first = FrameCatalog.frame(WorldSpace.declare("first").toOption.get)
    val second = FrameCatalog.frame(WorldSpace.declare("second").toOption.get)
    val placed = FramedSurface.in(first)(geometry, SurfacePlacement.StoredCoordinates).toOption.get
    val errors = compileErrors("val wrong: FramedSurface[second.type] = placed")
    assert(errors.contains("Required:") && errors.contains("FramedSurface"), errors)
    val huge = Affine.fromRowMajor[D3](Vector(1e308, 0, 0, 0, 0, 1e308, 0, 0, 0, 0, 1e308, 0, 0, 0, 0, 1)).toOption.get
    assert(FramedSurface.in(first)(SurfaceGeometry(mesh, geometry.hemisphere, geometry.kind, huge), SurfacePlacement.SurfaceToWorld).isLeft)
