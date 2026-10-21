package neurosurf4s.surface.reference

import image4s.geometry.{Affine, D3}

/** How a displacement point map is used by a bridge. */
enum PointMapUse:
  /** Apply the map: its input frame to its output frame. */
  case Forward
  /** Solve the map per point (reframe4s pointwise fixed-point inversion): its
    * output frame to its input frame. Each placement is an estimate with its
    * own residual, iteration count and status.
    */
  case Inverse(policy: InversePolicy)

  def label: String =
    this match
      case Forward => "forward"
      case Inverse(policy) =>
        s"pointwise inverse (approximate; tolerance ${policy.toleranceMm} mm, at most ${policy.maxIterations} iterations, " +
          s"divergence ratio ${policy.divergenceRatio})"

/** A numerical method behind an approximate bridge. Per-vertex evidence is in `BridgePlacement`. */
enum NumericalBridgeMethod:
  /** reframe4s-field `PointwiseInversion`: identity initialization, declared affines removed analytically. */
  case PointwiseFixedPoint(policy: InversePolicy)

  def label: String = this match
    case PointwiseFixedPoint(p) =>
      s"pointwise fixed-point inverse (reframe4s PointwiseInversion; tolerance ${p.toleranceMm} mm, " +
        s"at most ${p.maxIterations} iterations, divergence ratio ${p.divergenceRatio})"

/** Whether a bridge places points by applying declared transforms, or through
  * a numerical estimate whose accuracy is its per-vertex evidence.
  */
enum BridgeExactness:
  case Exact
  case Approximate(method: NumericalBridgeMethod)

  def label: String = this match
    case Exact => "exact"
    case Approximate(method) => s"approximate: ${method.label}"

/** The transform a bridge applies to world coordinates. */
enum BridgeTransform:
  /** One exact affine. */
  case AffineMap(matrix: Affine[D3])
  /** A digest-bound composite point map, applied per vertex. */
  case Displacement(pointMap: DeclaredPointMap, use: PointMapUse)

/** Explicit transform taking world coordinates in `from` to world coordinates
  * in `to`. Either an exact affine, or a digest-bound displacement point map
  * whose endpoints follow from its manifest frames and its declared use. A
  * route across any other gap is refused rather than approximated. `Affine`
  * values are immutable, finite, homogeneous and invertible by construction.
  */
final case class FrameBridge private (from: TemplateFrame, to: TemplateFrame, transform: BridgeTransform, evidence: String):
  def display: String =
    transform match
      case BridgeTransform.AffineMap(_) => s"affine ${from.display} -> ${to.display} ($evidence)"
      case BridgeTransform.Displacement(map, use) =>
        s"point map ${from.display} -> ${to.display}, ${use.label}, source ${map.source.display}, " +
          s"manifest sha256 ${map.manifest.sha256.value}" +
          s"${map.stageFiles.map(f => s", stage ${f.display}").mkString} ($evidence)"

  def exactness: BridgeExactness =
    transform match
      case BridgeTransform.AffineMap(_) => BridgeExactness.Exact
      case BridgeTransform.Displacement(_, PointMapUse.Forward) => BridgeExactness.Exact
      case BridgeTransform.Displacement(_, PointMapUse.Inverse(policy)) =>
        BridgeExactness.Approximate(NumericalBridgeMethod.PointwiseFixedPoint(policy))

object FrameBridge:
  def affine(from: TemplateFrame, to: TemplateFrame, matrix: Affine[D3], evidence: String): Either[ReferenceError, FrameBridge] =
    if from == to then Left(ReferenceError.InvalidBridge("source and target frames are identical"))
    else if evidence.trim.isEmpty then Left(ReferenceError.InvalidBridge("bridge evidence must be declared"))
    else Right(FrameBridge(from, to, BridgeTransform.AffineMap(matrix), evidence))

  /** A point-map bridge. Forward use runs from the map's input frame to its
    * output frame; inverse use runs the other way and requires a map the
    * provider can invert pointwise. Frame releases are the
    * map's catalog revision; `release` is required when the map has none and
    * must agree with it when both are present.
    */
  def displacement(pointMap: DeclaredPointMap, use: PointMapUse, release: Option[TemplateRelease] = None,
      evidence: String = "digest-bound point map"): Either[ReferenceError, FrameBridge] =
    val resolved = (pointMap.catalogRevision, release) match
      case (Some(declared), Some(requested)) if declared != requested =>
        Left(ReferenceError.InvalidBridge(s"release ${requested.value} differs from the map's catalog revision ${declared.value}"))
      case (Some(declared), _) => Right(declared)
      case (None, Some(requested)) => Right(requested)
      case (None, None) => Left(ReferenceError.InvalidBridge("point map has no catalog revision; declare the frame release"))
    for
      _ <- use match
        case PointMapUse.Inverse(policy) =>
          pointMap.map.inverter(policy).left.map(ReferenceError.PointwiseInverseUnsupported.apply)
        case PointMapUse.Forward => Right(())
      value <- resolved
      _ <- Either.cond(evidence.trim.nonEmpty, (), ReferenceError.InvalidBridge("bridge evidence must be declared"))
      input <- TemplateFrame.make(pointMap.input, value)
      output <- TemplateFrame.make(pointMap.output, value)
    yield use match
      case PointMapUse.Forward => FrameBridge(input, output, BridgeTransform.Displacement(pointMap, use), evidence)
      case PointMapUse.Inverse(_) => FrameBridge(output, input, BridgeTransform.Displacement(pointMap, use), evidence)

