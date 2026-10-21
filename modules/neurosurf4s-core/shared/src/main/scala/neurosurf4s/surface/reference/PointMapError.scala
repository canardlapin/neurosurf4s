package neurosurf4s.surface.reference

enum PointMapError:
  case InvalidField(reason: String)
  case InvalidStage(reason: String)
  case InvalidPolicy(reason: String)

  def message: String = this match
    case InvalidField(reason) => s"invalid displacement field: $reason"
    case InvalidStage(reason) => s"invalid point-map stage: $reason"
    case InvalidPolicy(reason) => s"invalid inverse policy: $reason"

