package neurosurf4s.surface.view

import neuroim4s.image.{WorldPoint, VoxelPoint}

final case class SurfaceLinkedSelection(selection: SurfaceSelection, world: WorldPoint, voxel: VoxelPoint)
