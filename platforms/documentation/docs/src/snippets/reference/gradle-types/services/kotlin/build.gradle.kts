import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyConstraintFactory
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.invocation.BuildInvocationDetails
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

plugins {
    id("application")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// tag::object-factory[]
tasks.register("myObjectFactoryTask") {
    val objectFactory = project.objects
    doLast {
        val myProperty = objectFactory.property(String::class)
        myProperty.set("Hello, Gradle!")
        println(myProperty.get())
    }
}
// end::object-factory[]

// tag::object-factory-inject[]
abstract class MyObjectFactoryTask
@Inject constructor(
    private val objectFactory: ObjectFactory,
    private val layout: ProjectLayout
) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = objectFactory.directoryProperty()
        outputDirectory.convention(layout.projectDirectory)
        println(outputDirectory.get())
    }
}

tasks.register("myInjectedObjectFactoryTask", MyObjectFactoryTask::class) {}
// end::object-factory-inject[]


// tag::project-layout[]
tasks.register("showLayout") {
    val layout = project.layout
    doLast {
        println("Project Directory: ${layout.projectDirectory}")
        println("Build Directory: ${layout.buildDirectory.get()}")
    }
}
// end::project-layout[]

// tag::project-layout-inject[]
abstract class MyProjectLayoutTask
@Inject constructor(private var projectLayout: ProjectLayout) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = projectLayout.projectDirectory
        println(outputDirectory)
    }
}

tasks.register("myInjectedProjectLayoutTask", MyProjectLayoutTask::class) {}
// end::project-layout-inject[]

// tag::provider-factory[]
tasks.register("printMessage") {
    val providerFactory = project.providers
    doLast {
        val messageProvider = providerFactory.provider { "Hello, Gradle!" }
        println(messageProvider.get())
    }
}
// end::provider-factory[]

// tag::provider-factory-inject[]
abstract class MyProviderFactoryTask
@Inject constructor(private var providerFactory: ProviderFactory) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = providerFactory.provider { "build/my-file.txt" }
        println(outputDirectory.get())
    }
}

tasks.register("myInjectedProviderFactoryTask", MyProviderFactoryTask::class) {}
// end::provider-factory-inject[]

// tag::worker-executor[]
abstract class MyWorkAction : WorkAction<WorkParameters.None> {
    private val greeting: String = "Hello from a Worker!"

    override fun execute() {
        println(greeting)
    }
}

abstract class MyWorkerTask
@Inject constructor(private var workerExecutor: WorkerExecutor) : DefaultTask() {
    @TaskAction
    fun doThings() {
        workerExecutor.noIsolation().submit(MyWorkAction::class.java) {}
    }
}

tasks.register("myWorkTask", MyWorkerTask::class) {}
// end::worker-executor[]

// tag::file-system-inject[]
abstract class MyFileSystemOperationsTask
@Inject constructor(private var fileSystemOperations: FileSystemOperations) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        fileSystemOperations.sync {
            from("src")
            into("dest")
        }
    }
}

tasks.register("myInjectedFileSystemOperationsTask", MyFileSystemOperationsTask::class)
// end::file-system-inject[]

// tag::file-system-adhoc[]
interface InjectedFsOps {
    @get:Inject val fs: FileSystemOperations
}

tasks.register("myAdHocFileSystemOperationsTask") {
    val injected = project.objects.newInstance<InjectedFsOps>()
    doLast {
        injected.fs.copy {
            from("src")
            into("dest")
        }
    }
}
// end::file-system-adhoc[]

val packageSources = tasks.register<Zip>("packageSources") {
    from(layout.projectDirectory) { include("*.gradle.kts") }
    archiveFileName = "sources.zip"
    destinationDirectory = layout.buildDirectory.dir("archives")
}

// tag::archive-op-inject[]
abstract class MyArchiveOperationsTask
@Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fs: FileSystemOperations
) : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun doTaskAction() {
        fs.sync {
            from(archiveOperations.zipTree(archiveFile))
            into(outputDirectory)
        }
    }
}

tasks.register("myInjectedArchiveOperationsTask", MyArchiveOperationsTask::class) {
    archiveFile = packageSources.flatMap { it.archiveFile }
    outputDirectory = layout.buildDirectory.dir("unpacked-sources")
}
// end::archive-op-inject[]

// tag::archive-op-adhoc[]
interface InjectedArcOps {
    @get:Inject val arcOps: ArchiveOperations
}

tasks.register("myAdHocArchiveOperationsTask") {
    val injected = project.objects.newInstance<InjectedArcOps>()
    val archiveFile = "${project.projectDir}/sources.jar"
    doLast {
        injected.arcOps.zipTree(archiveFile)
    }
}
// end::archive-op-adhoc[]

// tag::exec-op-inject[]
abstract class MyExecOperationsTask
@Inject constructor(private var execOperations: ExecOperations) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        execOperations.exec {
            commandLine("ls", "-la")
        }
    }
}

tasks.register("myInjectedExecOperationsTask", MyExecOperationsTask::class)
// end::exec-op-inject[]

// tag::exec-op-adhoc[]
interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}

tasks.register("myAdHocExecOperationsTask") {
    val injected = project.objects.newInstance<InjectedExecOps>()

    doLast {
        injected.execOps.exec {
            commandLine("ls", "-la")
        }
    }
}
// end::exec-op-adhoc[]

// tag::tooling-model[]
// Implements the ToolingModelBuilder interface.
// This interface is used in Gradle to define custom tooling models that can
// be accessed by IDEs or other tools through the Gradle tooling API.
class OrtModelBuilder : ToolingModelBuilder {
    private val repositories: MutableMap<String, String> = mutableMapOf()

    private val platformCategories: Set<String> = setOf("platform", "enforced-platform")

    private val visitedDependencies: MutableSet<ModuleComponentIdentifier> = mutableSetOf()
    private val visitedProjects: MutableSet<ModuleVersionIdentifier> = mutableSetOf()

    private val logger = Logging.getLogger(OrtModelBuilder::class.java)
    private val errors: MutableList<String> = mutableListOf()
    private val warnings: MutableList<String> = mutableListOf()

    override fun canBuild(modelName: String): Boolean {
        return false
    }

    override fun buildAll(modelName: String, project: Project): Any {
        return "model"
    }
}

// Plugin is responsible for registering a custom tooling model builder
// (OrtModelBuilder) with the ToolingModelBuilderRegistry, which allows
// IDEs and other tools to access the custom tooling model.
class OrtModelPlugin(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {
    override fun apply(project: Project) {
        registry.register(OrtModelBuilder())
    }
}
// end::tooling-model[]

// tag::build-invocation-details-inject[]
abstract class ReportBuildStartTask
@Inject constructor(private var invocationDetails: BuildInvocationDetails) : DefaultTask() {

    @TaskAction
    fun report() {
        val started = Date(invocationDetails.buildStartedTime)
        println("Build started at $started")
    }
}

tasks.register("reportBuildStart", ReportBuildStartTask::class) {}
// end::build-invocation-details-inject[]

// tag::dependency-constraint-factory-inject[]
abstract class CreateConstraintTask
@Inject constructor(private var factory: DependencyConstraintFactory) : DefaultTask() {

    @TaskAction
    fun create() {
        val constraint = factory.create("com.example:lib:1.2.3")
        println("Created constraint: ${constraint.group}:${constraint.name}:${constraint.version}")
    }
}

tasks.register("createConstraint", CreateConstraintTask::class) {}
// end::dependency-constraint-factory-inject[]

// tag::software-component-factory-inject[]
// A plugin that uses SoftwareComponentFactory to create an adhoc software
// component, suitable for publishing custom variants. The factory is only
// available via @Inject — there is no project-level accessor.
class CustomComponentPlugin
@Inject constructor(private val componentFactory: SoftwareComponentFactory) : Plugin<Project> {

    override fun apply(project: Project) {
        val component = componentFactory.adhoc("custom")
        project.components.add(component)
    }
}
// end::software-component-factory-inject[]

// tag::build-service-registry-inject[]
abstract class CounterService : BuildService<BuildServiceParameters.None> {
    private val count = AtomicInteger()

    fun increment(): Int = count.incrementAndGet()
}

class CounterPlugin
@Inject constructor(private val registry: BuildServiceRegistry) : Plugin<Project> {

    override fun apply(project: Project) {
        val counter = registry.registerIfAbsent("counter", CounterService::class.java) {}
        project.tasks.register("count") {
            usesService(counter)
            doLast {
                println("Count: ${counter.get().increment()}")
            }
        }
    }
}

apply<CounterPlugin>()
// end::build-service-registry-inject[]

// tag::java-toolchain-service-inject[]
abstract class PrintJavaVersionTask
@Inject constructor(toolchains: JavaToolchainService) : DefaultTask() {

    @get:Nested
    abstract val launcher: Property<JavaLauncher>

    init {
        launcher.convention(toolchains.launcherFor {
            languageVersion = JavaLanguageVersion.current()
        })
    }

    @TaskAction
    fun printVersion() {
        println("Java version: ${launcher.get().metadata.languageVersion}")
    }
}

tasks.register("printJavaVersion", PrintJavaVersionTask::class) {}
// end::java-toolchain-service-inject[]

// tag::build-events-listener-registry-inject[]
abstract class TaskEventsService : BuildService<BuildServiceParameters.None>, OperationCompletionListener {

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent) {
            println("Finished ${event.descriptor.taskPath}")
        }
    }
}

class TaskEventsPlugin
@Inject constructor(
    private val listenerRegistry: BuildEventsListenerRegistry,
    private val serviceRegistry: BuildServiceRegistry
) : Plugin<Project> {

    override fun apply(project: Project) {
        val service = serviceRegistry.registerIfAbsent("taskEvents", TaskEventsService::class.java) {}
        listenerRegistry.onTaskCompletion(service)
    }
}

apply<TaskEventsPlugin>()
// end::build-events-listener-registry-inject[]

// tag::clean-temp-inject[]
abstract class CleanTempTask
@Inject constructor(private val fs: FileSystemOperations) : DefaultTask() {

    @TaskAction
    fun run() {
        fs.delete { delete("build/tmp") }
    }
}

tasks.register("cleanTemp", CleanTempTask::class) {}
// end::clean-temp-inject[]

// Also exercise the Java implementation that lives in buildSrc, so the snippets
// integration test compiles and instantiates org.example.CleanTempTask.
tasks.register<org.example.CleanTempTask>("cleanTempJava") {}
