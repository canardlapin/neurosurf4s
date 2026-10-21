package neurosurf4s.surface.reference

import image4s.SomeSampleSpace

import image4s.geometry.{Affine, CoordinateConvention, D3, Frame, Grid, LengthUnit}
import neuroim4s.image.{SomeMaskVolume}
import neuroim4s.image.SampleSpaces
import neuroim4s.spaces.{FrameCatalog, WorldSpace}

/** The declared reference of a group-analysis volume: its template frame, its
  * exact voxel grid (dims and voxel-to-world affine in millimetres, RAS) and the
  * optional analysis support outside which values are not interpreted.
  *
  * The grid is the admitted image4s grid itself, so its identity carries the
  * grid's frame and persistent key rather than a detached copy of dims and
  * affine. Finite, invertible, homogeneous affines are guaranteed by `Affine`.
  */
final case class VolumeReference private (
  frame: TemplateFrame,
  grid: Grid[? <: Frame[D3], D3],
  support: Option[SomeMaskVolume]
):
  def dims: Vector[Int] = grid.shape

  def voxelToWorld: Affine[D3] = grid.indexToFrame

  /** Exact grid identity: the same live grid, or a grid in the same frame
    * (same live frame or same persistent frame key) with equal spatial dims and
    * a numerically equal voxel-to-world affine. Elements compare with `==`, so
    * `-0.0` equals `0.0`; persistent grid keys encode raw bits and would not.
    */
  def sharesGrid(other: Grid[?, ?]): Boolean =
    grid.sameRuntimeOwnerAs(other) || (
      (grid.frame.sameRuntimeOwnerAs(other.frame) || grid.frame.samePersistentKeyAs(other.frame)) &&
        grid.shape == other.shape &&
        grid.indexToFrame.rowMajor.corresponds(other.indexToFrame.rowMajor)(_ == _)
    )

object VolumeReference:
  def make(frame: TemplateFrame, space: SomeSampleSpace, support: Option[SomeMaskVolume] = None): Either[ReferenceError, VolumeReference] =
    SampleSpaces.requireVolumeD3(space).left.map(error => ReferenceError.InvalidVolumeGrid(error.message)).flatMap: spatial =>
      val coordinates = spatial.grid.frame
      val world = FrameCatalog.worldOf(coordinates)
      val conflict = world.toOption.exists:
        case WorldSpace.Template(name) => name.value != frame.template.value
        case _: WorldSpace.Unresolved => false
        case _ => true
      if coordinates.unit != LengthUnit.Millimeter || coordinates.convention != CoordinateConvention.RAS then
        Left(ReferenceError.InvalidVolumeGrid(s"expected RAS millimetres; got ${CoordinateConvention.serializedName(coordinates.convention)} ${LengthUnit.serializedName(coordinates.unit)}"))
      else if conflict then
        Left(ReferenceError.InvalidVolumeGrid(s"resolved world ${world.toOption.get.displayName} contradicts ${frame.display}"))
      else
        val reference = VolumeReference(frame, spatial.grid, support)
        support match
          case Some(mask) if !reference.sharesGrid(mask.grid) => Left(ReferenceError.SupportGridMismatch)
          case _ => Right(reference)
