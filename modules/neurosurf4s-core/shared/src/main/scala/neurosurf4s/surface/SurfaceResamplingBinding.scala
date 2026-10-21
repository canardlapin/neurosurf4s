package neurosurf4s.surface

import gale.linalg.LinAlgError
import gale.sparse.{CSR, Sparse, SparseValuePolicy}

enum SurfaceResamplingBindingError:
  case Endpoint(cause: SurfaceError)
  case UnsupportedNormalization(value: SurfaceResampling.Normalization)
  case Assembly(cause: LinAlgError)

  def message: String = this match
    case Endpoint(cause) => cause.message
    case UnsupportedNormalization(_) => "surface resampling compilation currently requires Element normalization"
    case Assembly(cause) => cause.getMessage

/** A checked binding to the plan's frozen ordered endpoint topologies.
  * Display coordinates may differ from the registration sphere coordinates.
  */
final class SurfaceResamplingBinding private (
    val plan: TemplateResamplingPlan,
    val sourceGeometry: SurfaceGeometry,
    val targetGeometry: SurfaceGeometry,
    val normalization: SurfaceResampling.Normalization,
    private val normalized: CSR
):
  /** Frozen Element-normalized weights prepared once at admission. */
  def normalizedCsr: CSR = normalized

  def validateEndpoints: Either[SurfaceResamplingBindingError, Unit] =
    SurfaceResamplingBinding.validateEndpoints(plan, sourceGeometry, targetGeometry)

object SurfaceResamplingBinding:
  def checked(
      plan: TemplateResamplingPlan,
      sourceGeometry: SurfaceGeometry,
      targetGeometry: SurfaceGeometry,
      normalization: SurfaceResampling.Normalization = SurfaceResampling.Normalization.Element
  ): Either[SurfaceResamplingBindingError, SurfaceResamplingBinding] =
    if normalization != SurfaceResampling.Normalization.Element then
      Left(SurfaceResamplingBindingError.UnsupportedNormalization(normalization))
    else
      for
        _ <- validateEndpoints(plan, sourceGeometry, targetGeometry)
        csr <- normalizedCsr(plan).left.map(SurfaceResamplingBindingError.Assembly.apply)
      yield new SurfaceResamplingBinding(plan, sourceGeometry, targetGeometry, normalization, csr)

  private def validateEndpoints(plan: TemplateResamplingPlan, source: SurfaceGeometry, target: SurfaceGeometry)
      : Either[SurfaceResamplingBindingError, Unit] =
    for
      _ <- plan.validateSourceGeometry(source).left.map(SurfaceResamplingBindingError.Endpoint.apply)
      _ <- plan.validateTargetGeometry(target).left.map(SurfaceResamplingBindingError.Endpoint.apply)
    yield ()

  private def normalizedCsr(plan: TemplateResamplingPlan): Either[LinAlgError, CSR] =
    val raw = plan.plan
    val weights = SurfaceResampling.normalised(raw.rows, raw.vals, raw.referenceVertices)
    Sparse.cooChecked(raw.referenceVertices, raw.movingVertices, SparseValuePolicy.RequireFinite).flatMap: builder =>
      var index = 0
      var error = Option.empty[LinAlgError]
      while index < weights.length && error.isEmpty do
        builder.tryAdd(raw.rows(index), raw.cols(index), weights(index)) match
          case Left(cause) => error = Some(cause)
          case Right(()) => ()
        index += 1
      error match
        case Some(cause) => Left(cause)
        case None => builder.tryToCOO().map(_.toCSR)
