package neurosurf4s.surface

import image4s.geometry.{D3, Frame, GeometryError, Point}
import reframe4s.core.{MapError, SpatialMap}

enum SurfaceTransportError:
  case Frame(error: SurfaceFrameError)
  case Map(error: MapError)

  def message: String = this match
    case Frame(error) => error.message
    case Map(error) => s"surface transport failed: ${error.message}"

extension [F <: Frame[D3]](surface: FramedSurface[F])
  /** Move every vertex through a typed provider map out of this surface's frame. Topology is unchanged. */
  def transport[G <: Frame[D3]](map: SpatialMap[F, G, D3]): Either[SurfaceTransportError, FramedSurface[G]] =
    val c = surface.mesh.coordinates
    val out = new Array[Double](c.length)
    var failure = Option.empty[SurfaceTransportError]
    FramedSurface.selfAlignment(surface.frame) match
      case Left(error) => failure = Some(SurfaceTransportError.Frame(error))
      case Right(self) =>
        var i = 0
        while i < surface.vertexCount && failure.isEmpty do
          val moved =
            for
              raw <- Point.fromVector[D3](surface.frame, Vector(c(3 * i), c(3 * i + 1), c(3 * i + 2))).left.map(error => SurfaceTransportError.Frame(SurfaceFrameError.Geometry(error)))
              point <- self.pointToRight(raw).left.map(GeometryError.fromSpatial).left.map(error => SurfaceTransportError.Frame(SurfaceFrameError.Geometry(error)))
              next <- map(point).left.map(SurfaceTransportError.Map.apply)
            yield next.coordinates
          moved match
            case Left(error) => failure = Some(error)
            case Right(xyz) if xyz.length == 3 && xyz.forall(_.isFinite) =>
              out(3 * i) = xyz(0)
              out(3 * i + 1) = xyz(1)
              out(3 * i + 2) = xyz(2)
            case Right(_) => failure = Some(SurfaceTransportError.Frame(SurfaceFrameError.NonFiniteVertex(i)))
          i += 1
    failure.toLeft(surface.moved(map.target, out))

