package neurosurf4s.routing

import image4s.Axis
import image4s.AxisKind

private[routing] object ProviderAxes:
  def time(extent: Int): Axis =
    Axis
      .ordinal("time", AxisKind.Time, extent)
      .fold(error => throw new IllegalArgumentException(error.message), identity)
