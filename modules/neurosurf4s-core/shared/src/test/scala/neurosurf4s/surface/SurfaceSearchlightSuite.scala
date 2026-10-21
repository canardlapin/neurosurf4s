package neurosurf4s.surface

import locus4s.{DomainId, Region}
import locus4s.data.VectorField
import neurosurf4s.surface.fixtures.SurfaceTestFixtures

class SurfaceSearchlightSuite extends munit.FunSuite:
  private val packed = SurfaceLocusDomain.semantic(DomainId.parse("subject-01:left-cortex").toOption.get, SurfaceTestFixtures.tetraGeometry).toOption.get
  private val domain = packed.value
  private val topology = SurfaceTestFixtures.tetraTopology

  test("zero radius includes exactly each embedded center"):
    val system = SurfaceSearchlight.metricBalls(domain, topology, 0.0).toOption.get.value
    assertEquals(system.membership.ordinalRows.map(_.toVector).toVector, Vector(Vector(0), Vector(1), Vector(2), Vector(3)))
    assertEquals(system.centerEmbedding.toTotalMap.targetOrdinals.toVector, Vector(0, 1, 2, 3))

  test("closed metric balls are symmetric and monotone and composition obeys summed radius"):
    val small = SurfaceSearchlight.metricBalls(domain, topology, 1.0).toOption.get.value.membership.ordinalRows.map(_.toVector).toVector
    val large = SurfaceSearchlight.metricBalls(domain, topology, 2.0).toOption.get.value.membership.ordinalRows.map(_.toVector).toVector
    assertEquals(small(0), Vector(0, 1, 2, 3))
    small.zipWithIndex.foreach: (members, center) =>
      members.foreach: neighbor =>
        assert(small(neighbor).contains(center))
        small(neighbor).foreach(target => assert(large(center).contains(target)))
      assert(members.forall(large(center).contains))

  test("sparse ambient centers 1 and 3 own exactly two compact rows and embed without renumbering"):
    val centers = Region.fromOrdinals(domain.finiteSpace, Vector(1, 3)).toOption.get
    val system = SurfaceSearchlight.metricBalls(domain, topology, 1.0, centers).toOption.get.value
    assertEquals(system.centers.size, 2)
    assertEquals(system.centerEmbedding.toTotalMap.targetOrdinals.toVector, Vector(1, 3))
    assertEquals(system.membership.ordinalRows.map(_.toVector).toVector, Vector(Vector(0, 1), Vector(0, 3)))
    assert(system.ambient.sameRuntimeOwnerAs(domain.finiteSpace))
    val field = VectorField.fromValues(domain.finiteSpace, Vector(10, 20, 30, 40)).toOption.get
    val section = SurfaceSearchlight.sectionAt(system.toNeighborhoodSystem, system.centers.indexOption(0).get, field)
    assertEquals(section.support.ordinalsInDomainOrder.toVector, Vector(0, 1))
    assertEquals(section.valuesInDomainOrder.toVector, Vector(10, 20))

  test("empty center support creates no rows and invalid radius or wrong topology is refused"):
    val centers = Region.fromOrdinals(domain.finiteSpace, Vector.empty).toOption.get
    val system = SurfaceSearchlight.metricBalls(domain, topology, 1.0, centers).toOption.get.value
    assertEquals(system.centers.size, 0)
    assertEquals(system.membership.ordinalRows.length, 0)
    assert(SurfaceSearchlight.metricBalls(domain, topology, -0.1).isLeft)
    assert(SurfaceSearchlight.metricBalls(domain, topology, Double.NaN).isLeft)
    assert(SurfaceSearchlight.metricBalls(domain, SurfaceTestFixtures.sheetTopology, 1.0).isLeft)

  test("disconnected components never enter finite-radius neighborhoods"):
    val disconnected = SurfaceGeometry(SurfaceTestFixtures.disconnectedGeometry.mesh, Hemisphere.Left, SurfaceKind.Pial)
    val d = SurfaceLocusDomain.semantic(DomainId.parse("disconnected").toOption.get, disconnected).toOption.get.value
    val system = SurfaceSearchlight.metricBalls(d, MeshTopology.from(disconnected.mesh), 1e6).toOption.get.value
    val rows = system.membership.ordinalRows.map(_.toVector).toVector
    assertEquals(rows(0), Vector(0, 1, 2))
    assertEquals(rows(3), Vector(3, 4, 5))

  test("a region from another hidden semantic owner cannot typecheck as these centers"):
    val errors = compileErrors("""
      val other = SurfaceLocusDomain.semantic(DomainId.parse("other").toOption.get, SurfaceTestFixtures.tetraGeometry).toOption.get
      SurfaceSearchlight.metricBalls(domain, topology, 1.0, Region.whole(other.value.finiteSpace))
    """)
    assert(errors.contains("Required:"), errors)
