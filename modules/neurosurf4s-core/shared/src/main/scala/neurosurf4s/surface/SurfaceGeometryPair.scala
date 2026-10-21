package neurosurf4s.surface

import scala.util.control.NonFatal

final case class SurfaceGeometryPair private (white: SurfaceGeometry, pial: SurfaceGeometry):
  require(white.vertexCount == pial.vertexCount, "white and pial surfaces must have the same vertex count")
  require(white.hemisphere == pial.hemisphere, "white and pial surfaces must have the same hemisphere")
  require(white.mesh.hasSameTopology(pial.mesh), "white and pial surfaces must share ordered triangle topology")

  require(white.mesh.topology eq pial.mesh.topology, "white and pial must share one topology owner")

  def copy(white: SurfaceGeometry = this.white, pial: SurfaceGeometry = this.pial): SurfaceGeometryPair =
    SurfaceGeometryPair(white, pial)

  def domainEither: Either[SurfaceError, SurfaceDomain] =
    for
      whiteDomain <- white.domainEither
      pialDomain <- pial.domainEither
      _ <- if whiteDomain == pialDomain then scala.util.Right(()) else scala.util.Left(SurfaceError.DomainMismatch(whiteDomain, pialDomain))
    yield whiteDomain

object SurfaceGeometryPair:
  def apply(white: SurfaceGeometry, pial: SurfaceGeometry): SurfaceGeometryPair =
    require(white.vertexCount == pial.vertexCount, "white and pial surfaces must have the same vertex count")
    require(white.hemisphere == pial.hemisphere, "white and pial surfaces must have the same hemisphere")
    require(white.mesh.hasSameTopology(pial.mesh), "white and pial surfaces must share ordered triangle topology")
    val mesh = pial.mesh.sharingTopology(white.mesh)
      .fold(error => throw new IllegalArgumentException(error.message), value => value)
    new SurfaceGeometryPair(white, SurfaceGeometry(mesh, pial.hemisphere, pial.kind, pial.surfaceToWorld))

  def fromEither(white: SurfaceGeometry, pial: SurfaceGeometry): Either[SurfaceError, SurfaceGeometryPair] =
    try scala.util.Right(SurfaceGeometryPair(white, pial))
    catch case NonFatal(error) => scala.util.Left(SurfaceError.InvalidGeometry(SurfaceError.reason(error)))

