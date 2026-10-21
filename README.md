# neurosurf4s

Surface anatomy, intrinsic geometry, fields and GIFTI/FreeSurfer ingestion for Scala 3 on JVM and Scala.js.

Core composes mesh4s topology/point fields, locus4s domains, spatial4s coordinates,
image4s geometry and neuroim4s world declarations. Core does not depend on volume,
registration, rendering, atlas, MVPA or ScalaFIM modules. IO, typed map transport,
and volume projection are optional modules. Extraction is in progress; no Root
cutover or runtime qualification is claimed until its recorded gates pass.

Raw surface coordinates use `spatial4s.Xyz` directly. Surface APIs retain no
parallel XYZ record or renaming facade. A raw triple carries no frame, units or
alignment; `FramedSurface` declares and checks the world owner separately.
