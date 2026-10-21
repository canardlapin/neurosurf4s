package neurosurf4s.surface

import neuroim4s.image.*

opaque type SurfaceDomainId = String

object SurfaceDomainId:
  def apply(value: String): SurfaceDomainId =
    val normalized = value.trim
    require(normalized.nonEmpty, "SurfaceDomainId must be non-empty")
    normalized

  extension (id: SurfaceDomainId)
    def value: String = id

enum SurfaceInverseKind:
  case Adjoint

enum SurfaceMorphismKind:
  case VolumeToSurface, SurfaceToSurface

sealed trait SurfaceMorphism:
  def source: SurfaceDomainId
  def target: SurfaceDomainId
  def kind: SurfaceMorphismKind
  def cost: Double
  def methodTag: String
  def inverseKind: SurfaceInverseKind

final case class VolToSurfMorphism(
    source: SurfaceDomainId,
    target: SurfaceDomainId,
    plan: VolumeSurfaceSamplingPlan,
    cost: Double = 10.0,
    methodTag: String = "volume-to-surface"
) extends SurfaceMorphism:
  require(cost.isFinite && cost >= 0.0, "surface morphism cost must be finite and non-negative")

  def kind: SurfaceMorphismKind =
    SurfaceMorphismKind.VolumeToSurface

  def inverseKind: SurfaceInverseKind =
    SurfaceInverseKind.Adjoint

  def sample(volume: SomeScalarVolume[Double], mask: Option[SomeMaskVolume] = None): SurfaceSampleResult =
    VolumeSurfaceSampler(plan).sample(volume, mask)

final case class SurfToSurfMorphism(
    source: SurfaceDomainId,
    target: SurfaceDomainId,
    mapping: SurfaceVertexMapping,
    cost: Double = 5.0,
    methodTag: String = "surface-to-surface"
) extends SurfaceMorphism:
  require(cost.isFinite && cost >= 0.0, "surface morphism cost must be finite and non-negative")

  def kind: SurfaceMorphismKind =
    SurfaceMorphismKind.SurfaceToSurface

  def inverseKind: SurfaceInverseKind =
    SurfaceInverseKind.Adjoint

  def resample(field: SurfaceField[Double]): SurfaceField[Double] =
    mapping(field)
