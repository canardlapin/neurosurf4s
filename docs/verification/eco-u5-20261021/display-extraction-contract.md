# Display extraction contract

Source: ScalaFIM 18c7b7725629a78cee5955df5c4448c4863dcb62.
Design review: volume_view_astra_review, 2026-10-21.

Move pure scenes, compiler, network, history, picking and raster to optional view/raster modules. Use spatial4s.Xyz for raw unowned coordinates. Retain typed display frame/pose APIs. Private validated camera extrema are display policy, not a public generic box or world authority. Preserve transform accumulation, homogeneous division, reduction order, center and diagonal formulas, Float-packed explicit fit and bilateral radius policy. Reject nonfinite centers.

Move typed SurfaceVolumeCursor to view-link over reframe core only. Move volume projection and SurfaceLinkedSelection to view-volume. Move SurfaceFeatureFixture to unpublished view-fixtures, used only by backend tests. Split mixed tests by dependency while retaining all assertions, literal historical matrices, affine/tie/snapshot witnesses and projection accounting.

Success requires owning JVM/JS suites and explicit Compile classpaths proving view/raster and view-link do not bring the volume stack. Root caller/deletion and native/browser qualification remain separate gates. Never rename persisted scalafim scene/profile/schema strings during namespace movement.
