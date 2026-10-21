package neurosurf4s.surface

import locus4s.{CenteredNeighborhoodSystem, Index, NeighborhoodSystem, NeighborhoodSystemError, Region, Relation, Selection, SpaceMismatch}
import locus4s.data.{Section, VectorField}

enum SurfaceSearchlightError:
  case InvalidRadius(value: Double)
  case TopologyMismatch(expected: String)
  case WrongSpace(error: SpaceMismatch)
  case InvalidNeighborhoods(error: NeighborhoodSystemError)

  def message: String = this match
    case InvalidRadius(value) => s"surface searchlight radius must be finite and non-negative, found $value"
    case TopologyMismatch(expected) => s"surface searchlight topology does not match $expected"
    case WrongSpace(error) => error.message
    case InvalidNeighborhoods(error) => error.message

/** The compact center owner is captured here; ambient vertices keep the topology's owner. */
trait SurfaceNeighborhoods[S]:
  type C
  val value: CenteredNeighborhoodSystem[C, S]

object SurfaceSearchlight:
  def metricBalls[S](
      domain: SurfaceLocusDomain[S],
      topology: MeshTopology,
      radius: Double,
      centers: Region[S],
      metric: DistanceMetric = DistanceMetric.Geodesic,
      edgeWeights: Option[Seq[Double]] = None
  ): Either[SurfaceSearchlightError, SurfaceNeighborhoods[S]] =
    if !radius.isFinite || radius < 0.0 then Left(SurfaceSearchlightError.InvalidRadius(radius))
    else if !domain.geometry.mesh.hasSameTopology(topology.mesh) then
      Left(SurfaceSearchlightError.TopologyMismatch(domain.meshDomain.display))
    else if !domain.finiteSpace.sameRuntimeOwnerAs(centers.space) then
      Left(SurfaceSearchlightError.WrongSpace(SpaceMismatch.between(domain.finiteSpace, centers.space)))
    else
      val centerOrdinals = centers.ordinalsInDomainOrder.toVector
      val selection = Selection.fromOrdinals(domain.finiteSpace, centerOrdinals).toOption.get
      val rows = Array.fill(centerOrdinals.length)(Array.emptyIntArray)
      if centerOrdinals.nonEmpty then
        val distances = SurfaceGeodesics.distanceMatrix(
          topology,
          centerOrdinals.map(VertexId.unsafe),
          Vector.tabulate(domain.finiteSpace.size)(VertexId.unsafe),
          metric,
          edgeWeights
        )
        var row = 0
        while row < distances.rows do
          val members = Array.newBuilder[Int]
          var col = 0
          while col < distances.cols do
            if distances(row, col) <= radius then members += distances.colVertices(col).index
            col += 1
          rows(row) = members.result()
          row += 1
      val membership = Relation.fromOrdinalRows(selection.positions, domain.finiteSpace, rows.iterator.map(_.iterator)).toOption.get
      CenteredNeighborhoodSystem.from(NeighborhoodSystem.fromSelection(selection, membership))
        .left.map(SurfaceSearchlightError.InvalidNeighborhoods.apply)
        .map: system =>
          new SurfaceNeighborhoods[S]:
            type C = selection.I
            val value: CenteredNeighborhoodSystem[C, S] = system

  def metricBalls[S](domain: SurfaceLocusDomain[S], topology: MeshTopology, radius: Double, metric: DistanceMetric): Either[SurfaceSearchlightError, SurfaceNeighborhoods[S]] =
    metricBalls(domain, topology, radius, Region.whole(domain.finiteSpace), metric)

  def metricBalls[S](domain: SurfaceLocusDomain[S], topology: MeshTopology, radius: Double): Either[SurfaceSearchlightError, SurfaceNeighborhoods[S]] =
    metricBalls(domain, topology, radius, DistanceMetric.Geodesic)

  def sectionAt[C, S, A](searchlight: NeighborhoodSystem[C, S], center: Index[C], field: VectorField[S, A]): Section[S, A] =
    field.restrict(searchlight.neighborhood(center))
