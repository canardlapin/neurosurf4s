package neurosurf4s.surface

import spatial4s.Xyz

type Point3D = Xyz
val Point3D: Xyz.type = Xyz

final case class Triangle(a: VertexId, b: VertexId, c: VertexId):
  def vertices: (VertexId, VertexId, VertexId) =
    (a, b, c)
