	import sbt._
	import Keys._

object SBinaryProject extends Build
{
	override lazy val projects = Seq(root, core, treeExample)

	lazy val root = Project("root", file(".")) settings( aux("SBinary Parent") : _*) aggregate(core, treeExample)
	lazy val core = Project("core", file("core")) settings(coreSettings : _*)
	lazy val treeExample = Project("examples", file("examples") / "bt") settings( aux("SBinary Tree Example") : _*) dependsOn(core)


	lazy val commonSettings: Seq[Setting[_]] = Seq(
		organization := "org.scala-tools.sbinary",
		version := "0.4.0",
		scalaVersion := "2.9.1",
                resolvers += ScalaToolsSnapshots,
		crossScalaVersions := Seq("2.10.0-SNAPSHOT"),
                publishMavenStyle := true
	)

        def cutVersion(version: String): String = {
          val pattern = "(\\d)\\.(\\d+)\\..*".r
          version match {
            case pattern(major, minor)=>
              major + "." + minor
            case _ =>
              error("Invalid Scala version")
              ""
          }
        }

	//lazy val scalaCheck = libraryDependencies <+= scalaVersion { sv =>
	//	val v = if(sv startsWith "2.9") "1.9" else "1.7"
	//	"org.scala-tools.testing" %% "scalacheck" % v % "test"
	//}
	lazy val coreSettings = commonSettings ++ template ++ Seq(
		name := "SBinary",
		//scalaCheck,
		//publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"),
                //publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))),
                publishTo in ThisBuild <<= publishResolver,
                credentials += Credentials(Path.userHome / ".ivy2" / ".typesafe-credentials"),
		unmanagedResources in Compile <+= baseDirectory map { _ / "LICENSE" }
	)// ++ localPublishSettings
/*        lazy val localPublishSettings = Seq(
          otherResolvers += Resolver.file("some-id", file("/tmp/sbt/publish")), 
          publishLocalConfiguration <<= (packagedArtifacts, deliverLocal, ivyLoggingLevel) map { 
           (arts, _, level) => sbt.Classpaths.publishConfig(arts, None, resolverName = "some-id", logging = level ) 
          }
        )*/
	def publishResolver: Project.Initialize[Option[Resolver]] = (scalaVersion) { (sv) =>
          Some(Resolver.url("typesafe-ide-" + cutVersion(sv), url("https://typesafe.artifactoryonline.com/typesafe/ide-" + cutVersion(sv)))(Resolver.mavenStylePatterns))}

	def aux(nameString: String) = commonSettings ++ Seq( publish := (), name := nameString )

	/*** Templating **/

	lazy val fmpp = TaskKey[Seq[File]]("fmpp")
	lazy val fmppOptions = SettingKey[Seq[String]]("fmpp-options")
	lazy val fmppConfig = config("fmpp") hide

//	lazy val template = fmppConfig(Test) ++ fmppConfig(Compile) ++ templateBase
	lazy val template = fmppConfig(Compile) ++ templateBase
	lazy val templateBase = Seq(
		libraryDependencies += "net.sourceforge.fmpp" % "fmpp" % "0.9.14" % fmppConfig.name,
		ivyConfigurations += fmppConfig,
		fmppOptions := "--ignore-temporary-files" :: Nil,
		fullClasspath in fmppConfig <<= update map { _ select configurationFilter(fmppConfig.name) map Attributed.blank }
	)
		
	def fmppConfig(c: Configuration): Seq[Setting[_]] = inConfig(c)(Seq(
		sourceGenerators <+= fmpp.identity,
		fmpp <<= fmppTask,
		scalaSource <<= (baseDirectory, configuration) { (base,c) => base / (Defaults.prefix(c.name) + "src") },
		mappings in packageSrc <<= (managedSources, sourceManaged) map { (srcs, base) => srcs x relativeTo(base) },
		sources <<= managedSources.identity
	))
	lazy val fmppTask =
		(fullClasspath in fmppConfig, runner in fmpp, unmanagedSources, scalaSource, sourceManaged, fmppOptions, streams) map { (cp, r, sources, srcRoot, output, args, s) =>
			IO.delete(output)
			val arguments = "-U" +: "all" +: "-S" +: srcRoot.getAbsolutePath +: "-O" +: output.getAbsolutePath +: (args ++ sources.getPaths)
			toError(r.run("fmpp.tools.CommandLine", cp.files, arguments, s.log))
			(output ** "*.scala").get
		}
}
