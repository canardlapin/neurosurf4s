package neurosurf4s.routing

import neuroim4s.spatial.SamplingPolicy

final case class OperatorCacheKey(signature: OperatorSignature):
  def source: DomainId =
    signature.source

  def target: DomainId =
    signature.target

  def path: Vector[MorphismId] =
    signature.recipe.path

  def routing: RoutingPolicy =
    signature.recipe.routing

  def sampling: SamplingPolicy =
    signature.recipe.sampling

  def rowSelection: RowSelection =
    signature.recipe.rowSelection

  def roi: Option[Vector[Int]] =
    signature.recipe.roi

  def allowInverses: Boolean =
    signature.recipe.allowInverses

  def compiler: String =
    signature.recipe.compiler

  def rows: Int =
    signature.shape.rows

  def cols: Int =
    signature.shape.cols

  def label: String =
    signature.label

object OperatorCacheKey:
  def apply(
    source: DomainId,
    target: DomainId,
    path: Vector[MorphismId],
    routing: RoutingPolicy,
    sampling: SamplingPolicy,
    roi: Option[Vector[Int]],
    allowInverses: Boolean,
    compiler: String,
    rows: Int,
    cols: Int
  ): OperatorCacheKey =
    val shape = OperatorShape.unsafe(rows, cols)
    val recipe =
      OperatorRecipe.unsafe(
        path,
        routing,
        sampling,
        RowSelection.unsafeFromRoi(roi),
        allowInverses,
        compiler
      )
    new OperatorCacheKey(OperatorSignature.unsafe(source, target, shape, recipe))

  def from(operator: SpatialOperator): OperatorCacheKey =
    new OperatorCacheKey(operator.signature)

