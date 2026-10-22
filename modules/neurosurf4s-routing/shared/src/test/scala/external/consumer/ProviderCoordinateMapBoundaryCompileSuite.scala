package external.consumer

import scala.compiletime.testing.typeCheckErrors

class ProviderCoordinateMapBoundaryCompileSuite extends munit.FunSuite:
  test("parallel spatial geometric variants cannot be imported or constructed"):
    val removed = Vector(
      "DenseCoordinateMap" ->
        typeCheckErrors("import neurosurf4s.routing.DenseCoordinateMap"),
      "CompositeCoordinateMap" ->
        typeCheckErrors("import neurosurf4s.routing.CompositeCoordinateMap"),
      "CoordinateMap.Affine3D" ->
        typeCheckErrors("val value = neurosurf4s.routing.CoordinateMap.Affine3D"),
      "CoordinateMap.Dense3D" ->
        typeCheckErrors("val value = neurosurf4s.routing.CoordinateMap.Dense3D"),
      "CoordinateMap.Composite3D" ->
        typeCheckErrors("val value = neurosurf4s.routing.CoordinateMap.Composite3D"),
      "ProviderMapBinding.copy" ->
        typeCheckErrors(
          """
            import image4s.geometry.{D3, Frame}
            import neuroim4s.image.SpatialPullback
            import neuroim4s.spatial.ProviderMapBinding

            def forge(binding: ProviderMapBinding, replacement: SpatialPullback[Frame[D3], Frame[D3]]) =
              binding.copy(pullback = replacement)
          """
        )
    )

    removed.foreach: (name, errors) =>
      assert(errors.nonEmpty, clue = s"parallel geometric API returned: $name")

  test("spatial routing exposes one provider-map payload"):
    val errors = typeCheckErrors(
      """
        import image4s.geometry.{D3, Frame}
        import neuroim4s.image.SpatialPullback
        import neurosurf4s.routing.CoordinateMap
        import neuroim4s.spatial.ProviderMapBinding

        def provider(map: CoordinateMap): Option[SpatialPullback[Frame[D3], Frame[D3]]] =
          map match
            case CoordinateMap.Geometric(binding: ProviderMapBinding) =>
              Some(binding.pullback)
            case _ => None
      """
    )

    assertEquals(errors, Nil)
