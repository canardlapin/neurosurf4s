package neurosurf4s.routing

/** A compiled spatial component paired with the complete, still unexecuted value program. */
final class CompiledCoordinateComponent private[routing] (
    val program: PullbackProgram,
    val operator: SpatialOperator
):
  def deferredValueStages: Vector[ValueStage] = program.valueStages
