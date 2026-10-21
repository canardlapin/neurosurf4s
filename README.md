# neurosurf4s

Surface anatomy, intrinsic geometry, fields and GIFTI/FreeSurfer ingestion for Scala 3 on JVM and Scala.js.

Core composes mesh4s topology/point fields, locus4s domains, spatial4s coordinates,
image4s geometry and neuroim4s world declarations. Core does not depend on volume,
registration, rendering, atlas, MVPA or ScalaFIM modules. IO, typed map transport,
and volume projection are optional modules. The standalone provider has JVM/JS qualification and an independent clean-clone
consumer. The ScalaFIM caller cutover is still in progress.

Raw surface coordinates use `spatial4s.Xyz` directly. Surface APIs retain no
parallel XYZ record or renaming facade. A raw triple carries no frame, units or
alignment; `FramedSurface` declares and checks the world owner separately.


| Module | Responsibility |
| --- | --- |
| `core` | Intrinsic meshes, fields, ordered topology, anatomy and world placement |
| `io` | GIFTI and FreeSurfer readers, platform compression and caches |
| `spatial` / `spatialIoJVM` | Explicit typed map transport and map readers |
| `volume` / `volumeIoJVM` | Optional volume projection, ribbon sampling and route IO |
| `fixtures` | Unpublished oracle resources and qualification consumers |

Run portable core/IO tests with `sbt neurosurf4sCoreTestAll`; optional suites have
`neurosurf4sSpatialTestAll`, `neurosurf4sVolumeTestAll` and
`neurosurf4sFixtureTestAll` aliases. The JVM map-reader module compiles in the
spatial gate; its behavioral oracle tests live in `fixtures`.

[Qualification receipts](docs/verification/eco-u5-20261021/default-pin-qualification.json)
record the exact source, test counts and resolved core/IO dependency boundaries.
Two JS timing opt-ins remain skipped. These gates do not establish native display,
registration estimation or new performance claims. Persisted ScalaFIM schema tags
and structural identity strings retain their original bytes across the move.
