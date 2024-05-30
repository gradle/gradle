import org.gradle.api.internal.FeaturePreviews
import java.io.PrintWriter
import java.io.Serializable

pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates")
            content {
                val rcAndMilestonesPattern = "\\d{1,2}?\\.\\d{1,2}?(\\.\\d{1,2}?)?-((rc-\\d{1,2}?)|(milestone-\\d{1,2}?))"
                // GE plugin marker artifact
                includeVersionByRegex("com.gradle.develocity", "com.gradle.develocity.gradle.plugin", rcAndMilestonesPattern)
                // GE plugin jar
                includeVersionByRegex("com.gradle", "develocity-gradle-plugin", rcAndMilestonesPattern)
            }
        }
        maven {
            name = "Gradle public repository"
            url = uri("https://repo.gradle.org/gradle/public")
            content {
                includeModule("org.openmbee.junit", "junit-xml-parser")
            }
        }
        gradlePluginPortal()
    }
    includeBuild("build-logic-settings")
}

plugins {
    id("gradlebuild.build-environment")
    id("com.gradle.develocity").version("3.17.4") // Sync with `build-logic-commons/build-platform/build.gradle.kts`
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.1")
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
//    id("net.ltgt.errorprone").version("3.1.0")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

val architectureElements = mutableListOf<ArchitectureElementBuilder>()

// If you include a new subproject here, consult internal documentation "Adding a new Build Tool subproject" page

unassigned {
    subproject("distributions-dependencies") // platform for dependency versions
    subproject("core-platform")              // platform for Gradle distribution core
}

// Gradle Distributions - for testing and for publishing a full distribution
unassigned {
    subproject("distributions-full")
}

// Gradle implementation projects
unassigned {
    subproject("core")
    subproject("build-events")
    subproject("diagnostics")
    subproject("composite-builds")
    subproject("core-api")
}

// Core platform
val core = platform("core") {

    // Core Runtime Module
    module("core-runtime") {
        subproject("base-asm")
        subproject("base-services")
        subproject("build-configuration")
        subproject("build-operations")
        subproject("build-option")
        subproject("build-process-services")
        subproject("build-profile")
        subproject("build-state")
        subproject("cli")
        subproject("client-services")
        subproject("concurrent")
        subproject("daemon-main")
        subproject("daemon-protocol")
        subproject("daemon-services")
        subproject("daemon-server")
        subproject("distributions-basics")
        subproject("distributions-core")
        subproject("file-temp")
        subproject("files")
        subproject("functional")
        subproject("gradle-cli-main")
        subproject("installation-beacon")
        subproject("instrumentation-agent")
        subproject("instrumentation-declarations")
        subproject("internal-instrumentation-api")
        subproject("internal-instrumentation-processor")
        subproject("io")
        subproject("java-language-extensions")
        subproject("launcher")
        subproject("logging")
        subproject("logging-api")
        subproject("messaging")
        subproject("native")
        subproject("process-services")
        subproject("serialization")
        subproject("service-provider")
        subproject("time")
        subproject("tooling-api-provider")
        subproject("wrapper-main")
        subproject("wrapper-shared")
    }

    // Core Configuration Module
    module("core-configuration") {
        subproject("api-metadata")
        subproject("base-services-groovy")
        subproject("configuration-cache")
        subproject("configuration-problems-base")
        subproject("core-kotlin-extensions")
        subproject("declarative-dsl-api")
        subproject("declarative-dsl-core")
        subproject("declarative-dsl-evaluator")
        subproject("declarative-dsl-provider")
        subproject("declarative-dsl-tooling-models")
        subproject("declarative-dsl-tooling-builders")
        subproject("file-collections")
        subproject("flow-services")
        subproject("graph-serialization")
        subproject("guava-serialization-codecs")
        subproject("input-tracking")
        subproject("kotlin-dsl")
        subproject("kotlin-dsl-provider-plugins")
        subproject("kotlin-dsl-tooling-builders")
        subproject("kotlin-dsl-tooling-models")
        subproject("kotlin-dsl-plugins")
        subproject("kotlin-dsl-integ-tests")
        subproject("stdlib-kotlin-extensions")
        subproject("stdlib-serialization-codecs")
        subproject("model-core")
        subproject("model-groovy")
    }

    // Core Execution Module
    module("core-execution") {
        subproject("build-cache")
        subproject("build-cache-base")
        subproject("build-cache-example-client")
        subproject("build-cache-local")
        subproject("build-cache-http")
        subproject("build-cache-packaging")
        subproject("build-cache-spi")
        subproject("execution-e2e-tests")
        subproject("file-watching")
        subproject("execution")
        subproject("hashing")
        subproject("persistent-cache")
        subproject("snapshots")
        subproject("worker-main")
        subproject("workers")
    }
}

// Documentation Module
module("documentation") {
    subproject("docs")
    subproject("docs-asciidoctor-extensions-base")
    subproject("docs-asciidoctor-extensions")
    subproject("samples")
}

// IDE Module
module("ide") {
    subproject("base-ide-plugins")
    subproject("ide")
    subproject("ide-native")
    subproject("ide-plugins")
    subproject("problems")
    subproject("problems-api")
    subproject("tooling-api")
    subproject("tooling-api-builders")
}

// Software Platform
val software = platform("software") {
    uses(core)
    subproject("antlr")
    subproject("build-init")
    subproject("dependency-management")
    subproject("plugins-distribution")
    subproject("distributions-publishing")
    subproject("ivy")
    subproject("maven")
    subproject("platform-base")
    subproject("plugins-version-catalog")
    subproject("publish")
    subproject("resources")
    subproject("resources-http")
    subproject("resources-gcs")
    subproject("resources-s3")
    subproject("resources-sftp")
    subproject("reporting")
    subproject("security")
    subproject("signing")
    subproject("testing-base")
    subproject("testing-base-infrastructure")
    subproject("test-suites-base")
    subproject("version-control")
}

// JVM Platform
val jvm = platform("jvm") {
    uses(core)
    uses(software)
    subproject("code-quality")
    subproject("distributions-jvm")
    subproject("ear")
    subproject("jacoco")
    subproject("jvm-services")
    subproject("language-groovy")
    subproject("language-java")
    subproject("language-jvm")
    subproject("toolchains-jvm")
    subproject("toolchains-jvm-shared")
    subproject("java-compiler-plugin")
    subproject("java-platform")
    subproject("normalization-java")
    subproject("platform-jvm")
    subproject("plugins-application")
    subproject("plugins-groovy")
    subproject("plugins-java")
    subproject("plugins-java-base")
    subproject("plugins-java-library")
    subproject("plugins-jvm-test-fixtures")
    subproject("plugins-jvm-test-suite")
    subproject("plugins-test-report-aggregation")
    subproject("scala")
    subproject("testing-jvm")
    subproject("testing-jvm-infrastructure")
    subproject("testing-junit-platform")
    subproject("war")
}

// Extensibility Platform
platform("extensibility") {
    uses(core)
    uses(jvm)
    subproject("plugin-use")
    subproject("plugin-development")
    subproject("unit-test-fixtures")
    subproject("test-kit")
}

// Native Platform
platform("native") {
    uses(core)
    uses(software)
    subproject("distributions-native")
    subproject("platform-native")
    subproject("language-native")
    subproject("tooling-native")
    subproject("testing-native")
}


// Develocity Module
module("enterprise") {
    subproject("enterprise")
    subproject("enterprise-logging")
    subproject("enterprise-operations")
    subproject("enterprise-plugin-performance")
    subproject("enterprise-workers")
}

testing {
    subproject("architecture-test")
    subproject("distributions-integ-tests")
    subproject("integ-test")
    subproject("internal-architecture-testing")
    subproject("internal-integ-testing")
    subproject("internal-performance-testing")
    subproject("internal-testing")
    subproject("performance")
    subproject("precondition-tester")
    subproject("soak")
    subproject("smoke-ide-test") // eventually should be owned by IDEX team
    subproject("smoke-test")
}

// Internal utility and verification projects
unassigned {
    subproject("internal-build-reports")
}

rootProject.name = "gradle"

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}

fun remoteBuildCacheEnabled(settings: Settings) = settings.buildCache.remote?.isEnabled == true

fun getBuildJavaHome() = System.getProperty("java.home")

gradle.settingsEvaluated {
    if ("true" == System.getProperty("org.gradle.ignoreBuildJavaVersionCheck")) {
        return@settingsEvaluated
    }

    if (!JavaVersion.current().isJava11) {
        throw GradleException("This build requires JDK 11. It's currently ${getBuildJavaHome()}. You can ignore this check by passing '-Dorg.gradle.ignoreBuildJavaVersionCheck=true'.")
    }
}

// region platform include DSL

gradle.rootProject {
    tasks.register("architectureDoc", GeneratorTask::class.java) {
        description = "Generates the architecture documentation"
        outputFile = layout.projectDirectory.file("architecture/platforms.md")
        elements = provider { architectureElements.map { it.build() } }
    }
}

abstract class GeneratorTask : DefaultTask() {
    private val markerComment = "<!-- This diagram is generated. Use `./gradlew :architectureDoc` to update it -->"
    private val startDiagram = "```mermaid"
    private val endDiagram = "```"

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val elements: ListProperty<ArchitectureElement>

    @TaskAction
    fun generate() {
        val markdownFile = outputFile.asFile.get()
        val head = if (markdownFile.exists()) {
            val content = markdownFile.readText().lines()
            val markerPos = content.indexOfFirst { it.contains(markerComment) }
            if (markerPos < 0) {
                throw IllegalArgumentException("Could not locate the generated diagram in $markdownFile")
            }
            val endPos = content.subList(markerPos, content.size).indexOfFirst { it.contains(endDiagram) && !it.contains(startDiagram) }
            if (endPos < 0) {
                throw IllegalArgumentException("Could not locate the end of the generated diagram in $markdownFile")
            }
            content.subList(0, markerPos)
        } else {
            emptyList()
        }

        markdownFile.bufferedWriter().use {
            PrintWriter(it).run {
                for (line in head) {
                    println(line)
                }
                graph(elements.get())
            }
        }
    }

    private fun PrintWriter.graph(elements: List<ArchitectureElement>) {
        println(
            """
            $markerComment
            $startDiagram
        """.trimIndent()
        )
        val writer = NodeWriter(this, "    ")
        writer.node("graph TD")
        for (element in elements) {
            if (element is Platform) {
                writer.platform(element)
            } else {
                writer.element(element)
            }
        }
        println(endDiagram)
    }

    private fun NodeWriter.platform(platform: Platform) {
        println()
        node("subgraph ${platform.id}[\"${platform.name} platform\"]") {
            for (child in platform.children) {
                element(child)
            }
        }
        node("end")
        node("style ${platform.id} fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;")
        for (dep in platform.uses) {
            node("${platform.id} --> $dep")
        }
    }

    private fun NodeWriter.element(element: ArchitectureElement) {
        println()
        node("${element.id}[\"${element.name} module\"]")
        node("style ${element.id} stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;")
    }

    private class NodeWriter(private val writer: PrintWriter, private val indent: String) {
        fun println() {
            writer.println()
        }

        fun node(node: String) {
            writer.print(indent)
            writer.println(node)
        }

        fun node(node: String, builder: NodeWriter.() -> Unit) {
            writer.print(indent)
            writer.println(node)
            builder(NodeWriter(writer, "$indent    "))
        }
    }
}

/**
 * Defines a top-level architecture module.
 */
fun module(moduleName: String, moduleConfiguration: ArchitectureModuleBuilder.() -> Unit) {
    val module = ArchitectureModuleBuilder(moduleName)
    architectureElements.add(module)
    module.moduleConfiguration()
}

/**
 * Defines a platform.
 */
fun platform(platformName: String, platformConfiguration: PlatformBuilder.() -> Unit): PlatformBuilder {
    val platform = PlatformBuilder(platformName)
    architectureElements.add(platform)
    platform.platformConfiguration()
    return platform
}

/**
 * Defines the testing module, for project helping test Gradle.
 */
fun testing(moduleConfiguration: ProjectScope.() -> Unit) =
    ProjectScope("testing").moduleConfiguration()

/**
 * Defines a bucket of unassigned projects.
 */
fun unassigned(moduleConfiguration: ProjectScope.() -> Unit) =
    ProjectScope("subprojects").moduleConfiguration()

class ProjectScope(
    private val basePath: String
) {
    fun subproject(projectName: String) {
        include(projectName)
        project(":$projectName").projectDir = file("$basePath/$projectName")
    }
}

class ElementId(val id: String) : Serializable {
    override fun toString(): String {
        return id
    }
}

sealed class ArchitectureElement(
    val name: String,
    val id: ElementId
) : Serializable

class Platform(name: String, id: ElementId, val uses: List<ElementId>, val children: List<ArchitectureModule>) : ArchitectureElement(name, id)

class ArchitectureModule(name: String, id: ElementId) : ArchitectureElement(name, id)

sealed class ArchitectureElementBuilder(
    val name: String
) {
    val id: ElementId = ElementId(name.replace("-", "_"))

    abstract fun build(): ArchitectureElement
}

class ArchitectureModuleBuilder(
    name: String,
    private val projectScope: ProjectScope
) : ArchitectureElementBuilder(name) {
    constructor(name: String) : this(name, ProjectScope("platforms/$name"))

    fun subproject(projectName: String) {
        projectScope.subproject(projectName)
    }

    override fun build(): ArchitectureModule {
        return ArchitectureModule(name, id)
    }
}

class PlatformBuilder(
    name: String,
    private val projectScope: ProjectScope
) : ArchitectureElementBuilder(name) {
    private val modules = mutableListOf<ArchitectureModuleBuilder>()
    private val uses = mutableListOf<PlatformBuilder>()

    constructor(name: String) : this(name, ProjectScope("platforms/$name"))

    fun subproject(projectName: String) {
        projectScope.subproject(projectName)
    }

    fun uses(platform: PlatformBuilder) {
        uses.add(platform)
    }

    fun module(platformName: String, moduleConfiguration: ArchitectureModuleBuilder.() -> Unit) {
        val module = ArchitectureModuleBuilder(platformName)
        modules.add(module)
        module.moduleConfiguration()
    }

    override fun build(): Platform {
        return Platform(name, id, uses.map { it.id }, modules.map { it.build() })
    }
}

// endregion
