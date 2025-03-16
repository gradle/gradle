import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileSystemOperations
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
    doLast {
        val objectFactory = project.objects
        val myProperty = objectFactory.property(String::class)
        myProperty.set("Hello, Gradle!")
        println(myProperty.get())
    }
}
// end::object-factory[]

// tag::object-factory-inject[]
abstract class MyObjectFactoryTask
@Inject constructor(private var objectFactory: ObjectFactory) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = objectFactory.directoryProperty()
        outputDirectory.convention(project.layout.projectDirectory)
        println(outputDirectory.get())
    }
}

tasks.register("myInjectedObjectFactoryTask", MyObjectFactoryTask::class) {}
// end::object-factory-inject[]


// tag::project-layout[]
tasks.register("showLayout") {
    doLast {
        val layout = project.layout
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
    doLast {
        val providerFactory = project.providers
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
    @get:Input
    abstract val booleanFlag: Property<Boolean>
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

// tag::archive-op-inject[]
abstract class MyArchiveOperationsTask
@Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val layout: ProjectLayout,
    private val fs: FileSystemOperations
) : DefaultTask() {
    @TaskAction
    fun doTaskAction() {
        fs.sync {
            from(archiveOperations.zipTree(layout.projectDirectory.file("sources.jar")))
            into(layout.buildDirectory.dir("unpacked-sources"))
        }
    }
}

tasks.register("myInjectedArchiveOperationsTask", MyArchiveOperationsTask::class)
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

    override fun buildAll(modelName: String, project: Project): Any? {
        return null
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
