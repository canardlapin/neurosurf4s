package neurosurf4s.surface

import image4s.geometry.{Affine, D3, Frame, FrameAlignment, GeometryError, Point}
import neuroim4s.spaces.{FrameCatalog, Placed, Rebind, SpaceError, WorldSpace}

/** Failures of placing surface vertices in a world frame or moving them between frames. */
enum SurfaceFrameError derives CanEqual:
  case Space(error: SpaceError)
  case Geometry(error: GeometryError)
  case NonFiniteVertex(vertex: Int)
  case VertexOutOfRange(vertex: Int, vertexCount: Int)
  case WrongWorld(role: String, expected: String, actual: WorldSpace)
  case SubjectMismatch(tkRas: WorldSpace, scanner: WorldSpace)
  case ReferenceMismatch(tkRas: WorldSpace, scanner: WorldSpace)

  def message: String =
    this match
      case Space(error)                     => error.message
      case Geometry(error)                  => s"surface frame geometry failed: ${error.message}"
      case NonFiniteVertex(vertex)          => s"vertex $vertex has a non-finite placed coordinate"
      case VertexOutOfRange(vertex, count)  => s"vertex $vertex is out of range for a surface with $count vertices"
      case WrongWorld(role, expected, actual) => s"$role frame must be $expected, got ${actual.displayName}"
      case SubjectMismatch(tkRas, scanner)  => s"tkRAS frame ${tkRas.displayName} and scanner frame ${scanner.displayName} belong to different subjects"
      case ReferenceMismatch(tkRas, scanner) =>
        s"tkRAS frame ${tkRas.displayName} is not anchored to the acquisition behind scanner frame ${scanner.displayName}"

/** Which coordinates of a [[SurfaceGeometry]] are the ones that live in the declared frame. */
enum SurfacePlacement derives CanEqual:
  /** The stored vertex coordinates (FreeSurfer surfaces: tkRAS; most GIFTI files: scanner RAS). */
  case StoredCoordinates

  /** The stored coordinates mapped through the geometry's `surfaceToWorld` (a GIFTI coordinate-system transform). */
  case SurfaceToWorld

/** A triangle mesh whose vertex coordinates are points in the world frame `F`.
  *
  * The frame is part of the type, so a FreeSurfer surface in tkRAS and a GIFTI surface in scanner RAS cannot be mixed:
  * moving between frames goes through a typed provider map in the optional spatial module, and for FreeSurfer's tkRAS through the
  * volume geometry in the optional volume module. Coordinates are stored once, row-major `x, y, z` per vertex, and are
  * always finite; the mesh is owned by this value and never handed out mutably.
  */
final class FramedSurface[F <: Frame[D3]] private (
    val frame: F,
    private[surface] val mesh: TriangleMesh,
    val hemisphere: Hemisphere,
    val kind: SurfaceKind
):
  def vertexCount: Int = mesh.vertexCount

  def faceCount: Int = mesh.faceCount

  /** Placed coordinates, row-major `x, y, z` per vertex, in frame `F`. */
  def coordinates: IArray[Double] = IArray.unsafeFromArray(mesh.coordinates.clone())

  /** The placed position of one vertex as a point owned by this surface's frame. */
  def vertex(id: VertexId): Either[SurfaceFrameError, Point[F, D3]] =
    val i = id.index
    if i >= vertexCount then Left(SurfaceFrameError.VertexOutOfRange(i, vertexCount))
    else
      FramedSurface.pointIn(frame, Vector(mesh.coordinates(3 * i), mesh.coordinates(3 * i + 1), mesh.coordinates(3 * i + 2)))

  /** The same surface as a plain geometry whose stored coordinates are the placed ones. */
  def geometry: SurfaceGeometry = SurfaceGeometry(mesh, hemisphere, kind)

  def hasSameTopology(other: FramedSurface[?]): Boolean = mesh.hasSameTopology(other.mesh)

  /** The world space of this surface's frame; fails for frames not made by [[FrameCatalog]]. */
  def world: Either[SurfaceFrameError, WorldSpace] =
    FrameCatalog.worldOf(frame).left.map(SurfaceFrameError.Space.apply)

  /** Internal attachment after an optional transport has checked every output coordinate. */
  private[surface] def moved[G <: Frame[D3]](target: G, coordinates: Array[Double]): FramedSurface[G] =
    new FramedSurface(target, mesh.withCoordinates(coordinates), hemisphere, kind)

  override def toString: String =
    s"FramedSurface(${hemisphere.code}, ${kind.label}, vertices=$vertexCount, frame=$frame)"

object FramedSurface:
  /** Declare that `placement` of `geometry` gives coordinates in `frame`. */
  def in(frame: Frame[D3])(geometry: SurfaceGeometry, placement: SurfacePlacement): Either[SurfaceFrameError, FramedSurface[frame.type]] =
    placed(geometry, placement).map(coords =>
      new FramedSurface[frame.type](frame, geometry.mesh.withCoordinates(coords), geometry.hemisphere, geometry.kind)
    )

  /** Place a geometry in the catalogued frame of `world`, as a dependent pair for code without a static frame. */
  def inWorld(world: WorldSpace)(geometry: SurfaceGeometry, placement: SurfacePlacement): Either[SurfaceFrameError, Placed[FramedSurface]] =
    val frame = FrameCatalog.frame(world)
    for
      surface <- in(frame)(geometry, placement)
      packed <- Placed[FramedSurface](frame)(surface).left.map(SurfaceFrameError.Space.apply)
    yield packed

  /** Move a framed surface across a checked frame alignment (same persistent key); coordinates are unchanged. */
  given rebind: Rebind[FramedSurface] with
    def toRight[A <: Frame[D3], B <: Frame[D3]](value: FramedSurface[A], alignment: FrameAlignment[D3, A, B]): Either[SpaceError, FramedSurface[B]] =
      if value.frame.sameRuntimeOwnerAs(alignment.left) then Right(new FramedSurface[B](alignment.right, value.mesh, value.hemisphere, value.kind))
      else Left(SpaceError.FrameBinding(s"surface frame ${value.frame} is not the alignment's left frame ${alignment.left}"))

  private[surface] def pointIn[F <: Frame[D3]](frame: F, coordinates: Vector[Double]): Either[SurfaceFrameError, Point[F, D3]] =
    for
      self <- selfAlignment(frame)
      raw <- Point.fromVector[D3](frame, coordinates).left.map(SurfaceFrameError.Geometry.apply)
      owned <- self.pointToRight(raw).left.map(GeometryError.fromSpatial).left.map(SurfaceFrameError.Geometry.apply)
    yield owned

  /** Evidence retyping points made at `frame.type` to the (possibly wider) static owner `F`. */
  private[surface] def selfAlignment[F <: Frame[D3]](frame: F): Either[SurfaceFrameError, FrameAlignment[D3, frame.type, F]] =
    Frame.alignOwners[D3, frame.type, F](frame, frame).left.map(SurfaceFrameError.Geometry.apply)

  private def placed(geometry: SurfaceGeometry, placement: SurfacePlacement): Either[SurfaceFrameError, Array[Double]] =
    placement match
      case SurfacePlacement.StoredCoordinates => Right(geometry.mesh.coordinates.clone())
      case SurfacePlacement.SurfaceToWorld    => applyAffine(geometry.surfaceToWorld, geometry.mesh.coordinates)

  private def applyAffine(affine: Affine[D3], coords: MeshCoordinates): Either[SurfaceFrameError, Array[Double]] =
    val m = affine.rowMajor.toArray
    val out = new Array[Double](coords.length)
    var i = 0
    var bad = -1
    while i < coords.length / 3 && bad < 0 do
      val (x, y, z) = (coords(3 * i), coords(3 * i + 1), coords(3 * i + 2))
      out(3 * i) = m(0) * x + m(1) * y + m(2) * z + m(3)
      out(3 * i + 1) = m(4) * x + m(5) * y + m(6) * z + m(7)
      out(3 * i + 2) = m(8) * x + m(9) * y + m(10) * z + m(11)
      if !(out(3 * i).isFinite && out(3 * i + 1).isFinite && out(3 * i + 2).isFinite) then bad = i
      i += 1
    if bad >= 0 then Left(SurfaceFrameError.NonFiniteVertex(bad)) else Right(out)

