plugins {
    id("java-library")
}

sourceSets {
    create("integTest")
}

val taskProvider = tasks.register<Test>("integTest") {
    classpath = sourceSets["integTest"].runtimeClasspath
    testClassesDirs = sourceSets["integTest"].output.classesDirs
}

val TaskContainer.integTest: TaskProvider<Test> get() = taskProvider // define accessor we would get if 'integTest' was defined in plugin

// tag::integTest[]
tasks.integTest {
    inputs.property("operatingSystem") {
        System.getProperty("os.name")
    }
}
// end::integTest[]

// tag::distributionPathInput[]
// Don't do this! Breaks relocatability!
tasks.integTest {
    systemProperty("distribution.location", layout.buildDirectory.dir("dist").get().asFile.absolutePath)
}
// end::distributionPathInput[]

// tag::distributionDirInput[]
abstract class DistributionLocationProvider : CommandLineArgumentProvider {  // <1>
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)  // <2>
    abstract val distribution: DirectoryProperty

    override fun asArguments(): Iterable<String> =
        listOf("-Ddistribution.location=${distribution.get().asFile.absolutePath}")  // <3>
}

tasks.integTest {
    jvmArgumentProviders.add(
        objects.newInstance<DistributionLocationProvider>().apply {  // <4>
            distribution = layout.buildDirectory.dir("dist")
        }
    )
}
// end::distributionDirInput[]

// tag::ignoreSystemProperties[]
abstract class CiEnvironmentProvider : CommandLineArgumentProvider {
    @get:Internal  // <1>
    abstract val agentNumber: Property<String>

    override fun asArguments(): Iterable<String> =
        listOf("-DagentNumber=${agentNumber.get()}")  // <2>
}

tasks.integTest {
    jvmArgumentProviders.add(
        objects.newInstance<CiEnvironmentProvider>().apply {  // <3>
            agentNumber = providers.environmentVariable("AGENT_NUMBER").orElse("1")
        }
    )
}
// end::ignoreSystemProperties[]

// tag::environment[]
tasks.integTest {
    inputs.property("langEnvironment") {
        System.getenv("LANG")
    }
}
// end::environment[]
