package neurosurf4s.routing

import gale.backend.Backend.given
import gale.linalg.DMat
import neuroim4s.image.SampleSpaces
import neuroim4s.spaces.SubjectId

class CoordinateComponentSuite extends munit.FunSuite:
  private def ok[E, A](value: Either[E, A]): A = value.fold(error => fail(s"$error"), identity)
  private def domain(id: String, count: Int): Domain =
    ok(Domain.build(ok(DomainId(id)), SpaceRef.Volume(ok(SubjectId("component-subject").asSpatial), None,
      ok(Modality("bold"))), ok(SamplingGeometry.volume(SampleSpaces(Vector(count, 1, 1),
        affine = Some(ProviderAffines.identity))))))

  private def valueCompiler(k: MorphismKind, name: String): MorphismPullbackCompiler = new MorphismPullbackCompiler:
    val id = ok(MorphismCompilerId(name))
    val kind = k
    def lower(morphism: Morphism, source: Domain, target: Domain): Either[SpatialError, Vector[PullbackStep]] =
      lowerProgram(morphism, source, target).map(_ => Vector.empty)
    override def lowerProgram(morphism: Morphism, source: Domain, target: Domain): Either[SpatialError, MorphismLowering] =
      for
        _ <- Morphism.validateDomains(morphism, source, target)
        stage <- ValueStage.fromPlugin(morphism, id)
      yield MorphismLowering(Vector(ProgramStage.Value(stage)))

  private val registry = ok(ok(MorphismCompilerRegistry.standard.register(
    valueCompiler(MorphismKind.Functional, "functional-value-plugin-v1"))).register(
    valueCompiler(MorphismKind.Filter, "filter-value-plugin-v1")))

  private def program(rowLinear: Boolean = false): PullbackProgram =
    val source = domain("source", 3)
    val sampled = domain("sampled", 2)
    val observations = domain("observations", 2)
    val target = domain("target", 2)
    val translation = ProviderAffines.fromRowMajor(Vector(
      1d, 0d, 0d, 1d, 0d, 1d, 0d, 0d, 0d, 0d, 1d, 0d, 0d, 0d, 0d, 1d))
    val spatial = ok(Morphism.between(ok(MorphismId("shift")), source, sampled, MorphismKind.Affine3D,
      RouteTag.Anatomical, coordinateMap = ok(CoordinateMap.affine(source, sampled, translation))))
    val observationMatrix = DMat.dense(2, 2, Vector(1d, 2d, 0d, 1d))
    val payload = if rowLinear then ok(MorphismPlugin.filter(ok(MorphismPluginId("rows")), observationMatrix))
      else ok(MorphismPlugin.functional(ok(MorphismPluginId("observations")), observationMatrix))
    val value = ok(Morphism.between(ok(MorphismId("value")), sampled, observations,
      if rowLinear then MorphismKind.Filter else MorphismKind.Functional, RouteTag.Functional, plugin = Some(payload)))
    val barrier = ok(Morphism.between(ok(MorphismId("offset")), observations, target, MorphismKind.Filter,
      RouteTag.Functional, plugin = Some(ok(MorphismPlugin.pointwiseBarrier(ok(MorphismPluginId("offset")), 2, 3)))))
    val graph = ok(SpatialGraph.build(Vector(source, sampled, observations, target), Vector(spatial, value, barrier)))
    ok(PullbackProgram.compile(graph, CompileRequest(source.id, target.id), registry))

  test("ordinary anatomical compilation refuses deferred values while a coordinate component keeps the complete program"):
    val original = program()
    assert(VolumePullbackOperatorCompiler.compile(original).isLeft)
    assert(VolumeAffineOperatorCompiler.compile(original).isLeft)
    assert(MixedPullbackOperatorCompiler.compile(original).isLeft)
    val component = ok(VolumePullbackOperatorCompiler.compileCoordinateComponent(original))
    assert(component.program eq original)
    assertEquals(component.program.fingerprint, original.fingerprint)
    assertEquals(component.operator.path.ids.map(_.value), Vector("shift", "value", "offset"))
    assertEquals(component.deferredValueStages.map(_.morphism.id.value), Vector("value", "offset"))
    val sampled = ok(component.operator.forward(DMat.dense(3, 2, Vector(1d, 10d, 2d, 20d, 3d, 30d))))
    assertEquals(sampled.toRows, Vector(Vector(2d, 20d), Vector(3d, 30d)))

  test("row-linear interleaving cannot be deferred by the coordinate-component seam"):
    assert(VolumePullbackOperatorCompiler.compileCoordinateComponent(program(rowLinear = true)).isLeft)
