package neurosurf4s.routing

opaque type FieldSourceId = String

object FieldSourceId:
  def apply(value: String): Either[SpatialError, FieldSourceId] =
    val normalized = value.trim
    if normalized.isEmpty then Left(SpatialError.EmptyIdentifier("field source"))
    else Right(normalized)

  private[routing] def unsafe(value: String): FieldSourceId =
    value

  extension (id: FieldSourceId)
    def value: String =
      id

