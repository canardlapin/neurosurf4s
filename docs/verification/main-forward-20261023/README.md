# Preserve main rendering behavior in the extracted owner

Qualified source: `4b23386bf750118b9c7c1220b6c592da9ba965e0`. Main remains unchanged.

The forward port retains equal-scale Pack layout across composition, raster, JavaFX, decorations and picking; worker coordinate snapshots and exact prepared-content comparisons; the guarded LegacyTriangle fast path; paired-camera capability/refusal; and per-surface native ambient-light lifecycle. Production source is unchanged from independently approved `16b32c4`; subsequent commits repair/add regression witnesses.

Executed checks: view 263 JVM/263 JS; raster 72/72; JavaFX 75; Three.js 51. Actual native probes pass worker publication/ownership/stale and invalid-camera refusal, World-Lambert FX material commits and Unlit restoration, two paired-camera picks, three four-face initial/update/camera/material-restoration cases, and 4144 composition picks including 1012 portrait-packed picks. Camera-only composition uploads remain 0. Two synthetic images were inspected for visible colour and vertical packing; this is not human product acceptance.

See [qualification.json](qualification.json) for source-bound checks, review scope and verified native artifact custody. Compressed raw logs include both earlier test-harness failures. Commands used JDK 22 and the provider's default OpenJFX 21.0.5; startup reports the existing unnamed-module configuration warning. This does not qualify the PLSNeuro OpenJFX 24 consumer, performance gates, hosted CI, or scientific acceptance. Production sampler restrictions remain intact.
