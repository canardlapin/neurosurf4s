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

lazy val spatial4sBuild = pinned("spatial4s", "f79674114dc5f1f88866708b74f7ea42e59895a8")
lazy val locus4sBuild = pinned("locus4s", "e32b0aeba5c4d19c457b2329272b95b26c061943")
lazy val mesh4sBuild = pinned("mesh4s", "35db08f864c86f211a14155fcaf6ef2fd811bcca")
lazy val image4sBuild = pinned("image4s", "aef5e223169f0831c942694c4b109ab20d152217")
lazy val graph4sBuild = pinned("graph4s", "613371f3bd0b12ce99352db0c637645eb62c6283")
lazy val zarr4sBuild = pinned("zarr4s", "2a5ba963b151b62c739d1bf5a19d49202bb6ff29")
lazy val neuroim4sBuild = pinned("neuroim4s", "21df6416e4e28e97cfbe39e20d7303349be7e4cf")
lazy val reframe4sBuild = pinned("reframe4s", "7ec7feff96d4057a86196b5e86d592688b7c7f84")

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
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-spatial")
  .jvmConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-fieldJVM"), ProjectRef(reframe4sBuild, "reframe4s-lieJVM"), ProjectRef(image4sBuild, "image4s-niftiJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(reframe4sBuild, "reframe4s-fieldJS"), ProjectRef(reframe4sBuild, "reframe4s-lieJS"), ProjectRef(image4sBuild, "image4s-niftiJS")))
  .jsSettings(jsSettings)

lazy val spatialIoJVM = project.in(file("modules/neurosurf4s-spatialIo/jvm"))
  .dependsOn(spatial.jvm % "compile->compile;test->test", io.jvm % "compile->compile;test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-spatial-io")

lazy val volume = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full).in(file("modules/neurosurf4s-volume"))
  .dependsOn(spatial % "compile->compile;test->test", core % "test->test")
  .settings(commonSettings).settings(name := "neurosurf4s-volume")
  .jvmConfigure(_.dependsOn(ProjectRef(neuroim4sBuild, "coreJVM")))
  .jsConfigure(_.dependsOn(ProjectRef(neuroim4sBuild, "coreJS")))
  .jsSettings(jsSettings)

lazy val volumeIoJVM = project.in(file("modules/neurosurf4s-volumeIo/jvm"))
  .dependsOn(volume.jvm % "compile->compile;test->test", io.jvm % "compile->compile;test->test")
  .dependsOn(ProjectRef(neuroim4sBuild, "ioJVM"))
  .settings(commonSettings).settings(name := "neurosurf4s-volume-io")

lazy val root = project.in(file("."))
  .aggregate(core.jvm, core.js, io.jvm, io.js, spatial.jvm, spatial.js, spatialIoJVM, volume.jvm, volume.js, volumeIoJVM)
  .settings(name := "neurosurf4s-root", publish / skip := true)

addCommandAlias("neurosurf4sCoreTestAll", ";coreJVM/test;coreJS/test;ioJVM/test;ioJS/test")
addCommandAlias("neurosurf4sSpatialTestAll", ";spatialJVM/test;spatialJS/test;spatialIoJVM/test")
addCommandAlias("neurosurf4sVolumeTestAll", ";volumeJVM/test;volumeJS/test;volumeIoJVM/test")
