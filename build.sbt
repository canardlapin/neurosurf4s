import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*
import sbtcrossproject.CrossType
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

ThisBuild / organization := "io.github.canardlapin"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))

def pinned(provider: String, revision: String): URI =
  sys.props.get(s"neurosurf4s.$provider.build")
    .map(path => file(path).getCanonicalFile.toURI)
    .getOrElse(uri(s"https://github.com/canardlapin/$provider.git#$revision"))

// Development overrides must resolve to one source owner across all providers.
val forwardedOverrides = Seq("image4s", "mesh4s", "reframe4s", "neuroim4s", "graph4s").foreach { consumer =>
  Seq("spatial4s", "image4s", "locus4s", "gale", "ravel", "reframe4s", "graph4s").foreach { provider =>
    sys.props.get(s"neurosurf4s.$provider.build").foreach(System.setProperty(s"$consumer.$provider.build", _))
  }
}

lazy val spatial4sRevision = "f79674114dc5f1f88866708b74f7ea42e59895a8"
lazy val spatial4sBuild = pinned("spatial4s", spatial4sRevision)
lazy val locus4sRevision = "e32b0aeba5c4d19c457b2329272b95b26c061943"
lazy val locus4sBuild = pinned("locus4s", locus4sRevision)
lazy val mesh4sRevision = "fd8b93ea42879ec5414342a084f05b831a4bbfc7"
lazy val mesh4sBuild = pinned("mesh4s", mesh4sRevision)
lazy val image4sRevision = "855a3a61029f78e975b43cd1736de1c94180c004"
lazy val image4sBuild = pinned("image4s", image4sRevision)
lazy val graph4sRevision = "613371f3bd0b12ce99352db0c637645eb62c6283"
lazy val graph4sBuild = pinned("graph4s", graph4sRevision)
lazy val zarr4sRevision = "2a5ba963b151b62c739d1bf5a19d49202bb6ff29"
lazy val zarr4sBuild = pinned("zarr4s", zarr4sRevision)
lazy val neuroim4sRevision = "4f75c2bf141449d032f9fe3c558ef13a22a4d59f"
lazy val neuroim4sBuild = pinned("neuroim4s", neuroim4sRevision)
lazy val reframe4sRevision = "622ab31f97bbe30a9bc6436464f3124643856f47"
lazy val reframe4sBuild = pinned("reframe4s", reframe4sRevision)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror", "-release:17"),
  Test / fork := false,
  libraryDependencies += "org.scalameta" %%% "munit" % "1.2.1" % Test
)
lazy val jsSettings = Seq(
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
)
lazy val coreBoundaryCheck = taskKey[Unit]("Reject volume, registration and application backedges from core")

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-core"))
  .settings(commonSettings)
  .settings(
    name := "neurosurf4s-core",
    libraryDependencies += "org.scala-lang.modules" %%% "scala-xml" % "2.4.0",
    coreBoundaryCheck := {
      val sources = ((LocalRootProject / baseDirectory).value / "modules/neurosurf4s-core/shared/src/main/scala" ** "*.scala").get
      val forbidden = "(?m)^\\s*import\\s+(?:scalafim|neuroim4s\\.image|neuroatlas4s|mvpa4s|reframe4s)\\.".r
      if (forbidden.findFirstIn("import neuroim4s.image.GridSpec").isEmpty || forbidden.findFirstIn("import scalafim.surface.X").isEmpty)
        sys.error("Core boundary detector failed its refusal witnesses")
      val violations = sources.filter(source => forbidden.findFirstIn(IO.read(source)).nonEmpty)
      if (violations.nonEmpty) sys.error(s"Core backedges: ${violations.mkString(", ")}")
    },
    Compile / compile := (Compile / compile).dependsOn(coreBoundaryCheck).value
  )
  .jvmConfigure(_.dependsOn(ProjectRef(spatial4sBuild, "spatial4s-coreJVM"), ProjectRef(locus4sBuild, "locus4s-dataJVM"), ProjectRef(mesh4sBuild, "mesh4s-geometryJVM"), ProjectRef(mesh4sBuild, "mesh4s-graph4sJVM"), ProjectRef(image4sBuild, "image4s-geometryJVM"), ProjectRef(graph4sBuild, "algorithmsJVM"), ProjectRef(zarr4sBuild, "coreJVM"), ProjectRef(neuroim4sBuild, "spacesJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(spatial4sBuild, "spatial4s-coreJS"), ProjectRef(locus4sBuild, "locus4s-dataJS"), ProjectRef(mesh4sBuild, "mesh4s-geometryJS"), ProjectRef(mesh4sBuild, "mesh4s-graph4sJS"), ProjectRef(image4sBuild, "image4s-geometryJS"), ProjectRef(graph4sBuild, "algorithmsJS"), ProjectRef(zarr4sBuild, "coreJS"), ProjectRef(neuroim4sBuild, "spacesJS")))
  .jsSettings(jsSettings)

lazy val io = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-io"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-io")
  .jsSettings(jsSettings)

lazy val spatial = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-spatial"))
  .dependsOn(core % "compile->compile;test->test", io % "test->compile")
  .settings(commonSettings).settings(name := "neurosurf4s-spatial", libraryDependencies += "com.lihaoyi" %%% "upickle" % "4.1.0")
  .jvmConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-fieldJVM"), ProjectRef(reframe4sBuild, "reframe4s-lieJVM"), ProjectRef(image4sBuild, "image4s-niftiJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-fieldJS"), ProjectRef(reframe4sBuild, "reframe4s-lieJS"), ProjectRef(image4sBuild, "image4s-niftiJS")))
  .jsSettings(jsSettings)

lazy val spatialIoJVM = project.in(file("modules/neurosurf4s-spatial-io/jvm"))
  .dependsOn(spatial.jvm % "compile->compile;test->test", io.jvm % "compile->compile;test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-spatial-io")

lazy val volume = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-volume"))
  .dependsOn(spatial % "compile->compile;test->test", core % "test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-volume")
  .jvmConfigure(_.dependsOn(ProjectRef(neuroim4sBuild, "coreJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(neuroim4sBuild, "coreJS")))
  .jsSettings(jsSettings)

lazy val volumeIoJVM = project.in(file("modules/neurosurf4s-volume-io/jvm"))
  .dependsOn(volume.jvm % "compile->compile;test->test", io.jvm % "compile->compile;test->test")
  .dependsOn(ProjectRef(neuroim4sBuild, "ioJVM"))
  .settings(commonSettings).settings(name := "neurosurf4s-volume-io")

// Unpublished independent oracle bytes and qualification consumers; production modules never depend on this.
lazy val fixtures = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-test-fixtures"))
  .dependsOn(volume, io, viewVolume)
  .settings(commonSettings).settings(name := "neurosurf4s-test-fixtures", publish / skip := true)
  .jvmConfigure(_.dependsOn(spatialIoJVM, volumeIoJVM))
  .jsSettings(jsSettings)

// Optional surface display; scientific volume and linking integrations remain separate.
lazy val intaglioRevision = "d553c327824f9916b1c0a055226c6c5e8f77bc59"
lazy val intaglioBuild = pinned("intaglio", intaglioRevision)

lazy val view = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view"))
  .dependsOn(core)
  .settings(commonSettings).settings(name := "neurosurf4s-view")
  .jvmConfigure(_.dependsOn(ProjectRef(intaglioBuild, "coreJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(intaglioBuild, "coreJS")))
  .jsSettings(jsSettings)

lazy val viewLink = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view-link"))
  .dependsOn(view)
  .settings(commonSettings).settings(name := "neurosurf4s-view-link")
  .jvmConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-coreJVM"), ProjectRef(reframe4sBuild, "reframe4s-lieJVM") % "test->compile"))
  .jsConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-coreJS"), ProjectRef(reframe4sBuild, "reframe4s-lieJS") % "test->compile"))
  .jsSettings(jsSettings)

lazy val viewVolume = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view-volume"))
  .dependsOn(view, volume, viewLink % "test->compile")
  .settings(commonSettings).settings(name := "neurosurf4s-view-volume")
  .jsSettings(jsSettings)

lazy val viewRaster = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view-raster"))
  .dependsOn(view, fixtures % "test->compile")
  .settings(commonSettings).settings(name := "neurosurf4s-view-raster")
  .jsSettings(jsSettings)

lazy val javafxPlatformClassifier: String = {
  val os = sys.props.getOrElse("os.name", "").toLowerCase
  val arch = sys.props.getOrElse("os.arch", "").toLowerCase
  val base = if (os.contains("mac")) "mac" else if (os.contains("win")) "win" else "linux"
  if (arch.contains("aarch64") && base != "win") base + "-aarch64" else base
}

lazy val viewJavafxJVM = project.in(file("modules/neurosurf4s-view-javafx/jvm"))
  .dependsOn(view.jvm, viewRaster.jvm, fixtures.jvm % "test->compile")
  .settings(commonSettings)
  .settings(name := "neurosurf4s-view-javafx", Test / fork := true, Test / run / fork := true,
    libraryDependencies ++= Seq(
      "org.openjfx" % "javafx-base" % "21.0.5" % Provided classifier javafxPlatformClassifier,
      "org.openjfx" % "javafx-graphics" % "21.0.5" % Provided classifier javafxPlatformClassifier))

lazy val viewThree = crossProject(JSPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view-three"))
  .dependsOn(view, viewRaster, fixtures % "test->compile")
  .settings(commonSettings).settings(name := "neurosurf4s-view-three")
  .jsSettings(jsSettings)

lazy val viewThreeVolume = crossProject(JSPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-view-three-volume"))
  .dependsOn(viewThree, viewVolume, fixtures % "test->compile")
  .settings(commonSettings).settings(name := "neurosurf4s-view-three-volume")
  .jsSettings(jsSettings)

lazy val viewExamples = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("examples/surface-view"))
  .dependsOn(viewRaster, viewLink, viewVolume, fixtures, io)
  .settings(commonSettings).settings(name := "neurosurf4s-examples-surface-view", publish / skip := true,
    libraryDependencies += "org.scala-lang.modules" %%% "scala-xml" % "2.4.0")
  .jvmConfigure(_.dependsOn(viewJavafxJVM, spatialIoJVM, volumeIoJVM, ProjectRef(intaglioBuild, "java2dJVM") % "test->compile"))
  .jvmSettings(Test / run / fork := true,
    libraryDependencies ++= Seq(
      "org.openjfx" % "javafx-base" % "21.0.5" classifier javafxPlatformClassifier,
      "org.openjfx" % "javafx-graphics" % "21.0.5" classifier javafxPlatformClassifier))
  .jsConfigure(_.dependsOn(viewThree.js, viewThreeVolume.js))
  .jsSettings(jsSettings)

lazy val surfaceExamplesJVM = project.in(file("examples/surface-jvm"))
  .dependsOn(volumeIoJVM, spatialIoJVM)
  .settings(commonSettings).settings(name := "neurosurf4s-examples-surface-jvm", publish / skip := true)

lazy val root = project.in(file("."))
  .aggregate(core.jvm, core.js, io.jvm, io.js, spatial.jvm, spatial.js, spatialIoJVM, volume.jvm, volume.js, volumeIoJVM, fixtures.jvm, fixtures.js, view.jvm, view.js, viewLink.jvm, viewLink.js, viewVolume.jvm, viewVolume.js, viewRaster.jvm, viewRaster.js, viewJavafxJVM, viewThree.js, viewThreeVolume.js, viewExamples.jvm, viewExamples.js, surfaceExamplesJVM)
  .settings(name := "neurosurf4s-root", publish / skip := true)

addCommandAlias("neurosurf4sCoreTestAll", ";coreJVM/test;coreJS/test;ioJVM/test;ioJS/test")
addCommandAlias("neurosurf4sSpatialTestAll", ";spatialJVM/test;spatialJS/test;spatialIoJVM/test")
addCommandAlias("neurosurf4sVolumeTestAll", ";volumeJVM/test;volumeJS/test;volumeIoJVM/test")

addCommandAlias("neurosurf4sFixtureTestAll", ";fixturesJVM/test;fixturesJS/test")

addCommandAlias("neurosurf4sViewTestAll", ";viewJVM/test;viewJS/test;viewLinkJVM/test;viewLinkJS/test;viewVolumeJVM/test;viewVolumeJS/test;viewRasterJVM/test;viewRasterJS/test")

addCommandAlias("neurosurf4sBackendTestAll", ";viewJavafxJVM/test;viewThreeJS/test;viewThreeVolumeJS/test;viewExamplesJVM/test;viewExamplesJS/test;surfaceExamplesJVM/test")
