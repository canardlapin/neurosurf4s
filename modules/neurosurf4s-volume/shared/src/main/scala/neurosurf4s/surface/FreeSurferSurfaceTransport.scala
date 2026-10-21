package neurosurf4s.surface

import image4s.geometry.{D3, Frame}
import reframe4s.lie.FramedAffine
import neuroim4s.image.world.FreeSurferVolumeGeometry
import neuroim4s.spaces.{FrameCatalog, WorldSpace}

extension [F <: Frame[D3]](surface: FramedSurface[F])
  /** Move a FreeSurfer tkRAS surface into the subject's scanner RAS with `Norig * inverse(Torig)` of the volume that
    * defines its tkRAS (usually `orig.mgz`). This surface's frame must be a [[WorldSpace.SubjectTkRas]] frame and
    * `scanner` a [[WorldSpace.SubjectNative]] frame of the same dataset and subject whose reference acquisition is the
    * tkRAS space's conformed volume: `Norig` lands in that acquisition's scanner RAS, and other sessions or references
    * are distinct spaces until a coregistration says otherwise. The caller vouches that `geometry` is the header of
    * that conformed volume; nothing here can check it.
    */
  def toScanner(scanner: Frame[D3], geometry: FreeSurferVolumeGeometry): Either[SurfaceTransportError, FramedSurface[scanner.type]] =
    for
      _ <- checkTkRasPair(surface.frame, scanner).left.map(SurfaceTransportError.Frame.apply)
      moved <- surface.transport(FramedAffine.betweenFrames[F, scanner.type, D3](surface.frame, scanner)(geometry.tkrToScanner))
    yield moved

  /** Inverse of [[toScanner]]: move a scanner-RAS surface into the tkRAS of the volume `geometry` describes. */
  def toTkRas(tkRas: Frame[D3], geometry: FreeSurferVolumeGeometry): Either[SurfaceTransportError, FramedSurface[tkRas.type]] =
    for
      _ <- checkTkRasPair(tkRas, surface.frame).left.map(SurfaceTransportError.Frame.apply)
      moved <- surface.transport(FramedAffine.betweenFrames[F, tkRas.type, D3](surface.frame, tkRas)(geometry.tkrToScanner.inverse))
    yield moved


private def checkTkRasPair(tkRas: Frame[D3], scanner: Frame[D3]): Either[SurfaceFrameError, Unit] =
  for
    tkWorld <- FrameCatalog.worldOf(tkRas).left.map(SurfaceFrameError.Space.apply)
    scWorld <- FrameCatalog.worldOf(scanner).left.map(SurfaceFrameError.Space.apply)
    _ <- (tkWorld, scWorld) match
      case (WorldSpace.SubjectTkRas(ns, sub, conformed), WorldSpace.SubjectNative(ns2, sub2, _, reference)) =>
        if ns != ns2 || sub != sub2 then Left(SurfaceFrameError.SubjectMismatch(tkWorld, scWorld))
        else if conformed != reference then Left(SurfaceFrameError.ReferenceMismatch(tkWorld, scWorld))
        else Right(())
      case (WorldSpace.SubjectTkRas(_, _, _), other) =>
        Left(SurfaceFrameError.WrongWorld("scanner", "a subject-native (scanner RAS) space", other))
      case (other, _) =>
        Left(SurfaceFrameError.WrongWorld("tkRAS", "a FreeSurfer tkRAS space", other))
  yield ()
