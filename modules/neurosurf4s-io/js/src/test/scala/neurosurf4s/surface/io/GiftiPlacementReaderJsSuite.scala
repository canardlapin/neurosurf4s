package neurosurf4s.surface.io

import neurosurf4s.surface.*
import neurosurf4s.surface.gifti.*

class GiftiPlacementReaderJsSuite extends GiftiPlacementReaderChecks:
  def decode(document: GiftiDocument, selection: GiftiTransformSelection) =
    GiftiSurfaceReader.declared(document, Hemisphere.Left, SurfaceKind.Pial, selection)
  def convenience(document: GiftiDocument) =
    GiftiSurfaceReader.geometry(document, Hemisphere.Left, SurfaceKind.Pial)
