package neurosurf4s.surface

final case class SurfaceVertexMapping private (
    sourceGeometry: SurfaceGeometry,
    targetGeometry: SurfaceGeometry,
    sourceForTarget: Vector[VertexId]
):
  require(sourceForTarget.length == targetGeometry.vertexCount, "mapping must define one source vertex per target vertex")
  sourceForTarget.foreach { vertex =>
    require(vertex.index < sourceGeometry.vertexCount, "source vertex mapping out of range")
  }

  def apply(field: SurfaceField[Double], label: Option[String] = None): SurfaceField[Double] =
    require(field.geometry == sourceGeometry, "surface field geometry must match mapping source")
    val values =
      sourceForTarget.map { vertex =>
        field.valueAt(vertex).getOrElse(Double.NaN)
      }
    SurfaceField.full(targetGeometry, values, label.getOrElse(field.label))

object SurfaceVertexMapping:
  def nearestIndex(
      sourceGeometry: SurfaceGeometry,
      targetGeometry: SurfaceGeometry,
      sourceForTarget: Vector[VertexId]
  ): SurfaceVertexMapping =
    SurfaceVertexMapping(sourceGeometry, targetGeometry, sourceForTarget)

