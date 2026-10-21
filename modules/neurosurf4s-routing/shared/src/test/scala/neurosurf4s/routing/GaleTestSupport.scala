package neurosurf4s.routing

import gale.linalg.{DMat, LinAlgError}
import gale.sparse.{COO, CSR}

private[routing] type DoubleMatrix = DMat
private[routing] type LinearMapError = LinAlgError
private[routing] type CsrMatrix = CSR
private[routing] type SparseTriplets = COO

private[routing] object DoubleMatrix:
  def zeros(rows: Int, cols: Int): DMat =
    DMat.zeros(rows, cols)

  def fromRows(rows: Seq[Seq[Double]]): DMat =
    GaleSpatialSupport.matrixFromRows(rows)

  def unsafe(rows: Int, cols: Int, values: Array[Double]): DMat =
    GaleSpatialSupport.unsafeOwnedMatrix(rows, cols, values)

private[routing] object SparseTriplets:
  def apply(
    rows: Int,
    cols: Int,
    rowIndices: Array[Int],
    colIndices: Array[Int],
    values: Array[Double]
  ): Either[LinAlgError, COO] =
    GaleSpatialSupport.sparseTriplets(rows, cols, rowIndices, colIndices, values)
