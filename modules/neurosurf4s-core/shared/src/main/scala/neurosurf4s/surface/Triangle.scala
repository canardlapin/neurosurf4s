package neurosurf4s.surface

final case class Triangle(a: VertexId, b: VertexId, c: VertexId):
  def vertices: (VertexId, VertexId, VertexId) =
    (a, b, c)
