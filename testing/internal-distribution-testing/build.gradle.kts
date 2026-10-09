import gradlebuild.integrationtests.tasks.GenerateLanguageAnnotations
import java.util.Properties

plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for distribution tests, internal use only"

jvmCompile {
    compilations {
        named("main") {
            // These test fixtures are used by the tooling API tests, which still run on JVM 8
            targetJvmVersion = 8
        }
    }
}

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.daemonProtocol)
    api(projects.hashing)
    api(projects.internalTesting)
    api(projects.jvmServices)
    api(projects.stdlibJavaExtensions)
    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.gson)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.slf4jApi)
    api(testLibs.hamcrest)
    api(testLibs.junit)
    api(testLibs.samplesCheck)
    api(testLibs.spock)

    implementation(projects.baseServicesGroovy)
    implementation(projects.buildProcessServices)
    implementation(projects.credentialsApi)
    implementation(projects.buildOperations)
    implementation(projects.clientServices)
    implementation(projects.concurrent)
    implementation(projects.coreApi)
    implementation(projects.daemonLogging)
    implementation(projects.daemonMessaging)
    implementation(projects.enterpriseLogging)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.launcher)
    implementation(projects.logging)
    implementation(projects.native)
    implementation(projects.problemsApi)
    implementation(projects.processServicesBase)
    implementation(projects.serviceLookup)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.time)
    implementation(projects.toolingApi)
    implementation(projects.wrapperShared)
    implementation(projects.workerShared)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.groovyJson)
    implementation(libs.httpcore)
    implementation(libs.ivy)
    implementation(libs.jcifs)
    implementation(libs.nativePlatform)
    implementation(testLibs.ansiControlSequenceUtil)
    implementation(testLibs.junit5JupiterApi)

    compileOnly(libs.jetbrainsAnnotations)
    compileOnly(libs.jspecify)

    integTestDistributionRuntimeOnly(projects.distributionsCore)

    // Javadoc-only: for {@link} references to types in internal-integ-testing and tooling-api test fixtures
    javadocReferences(projects.internalIntegTesting)
    javadocReferences(testFixtures(projects.toolingApi))
}

// Declared separately from the task so the source set can register it as an output directory
// without querying the task's output property, which is not allowed before the task has run.
val allReleasedVersionsDir = layout.buildDirectory.dir("generated-resources/all-released-versions")

val prepareVersionsInfo = tasks.register<PrepareVersionsInfo>("prepareVersionsInfo") {
    group = "build"
    description = "Generates the properties file listing all previously released Gradle versions."
    destFile = allReleasedVersionsDir.map { it.file("all-released-versions.properties") }
    versions = gradleModule.identity.releasedVersions.map {
        it.allPreviousVersions.joinToString(" ") { it.version }
    }
    mostRecent = gradleModule.identity.releasedVersions.map { it.mostRecentRelease.version }
    mostRecentSnapshot = gradleModule.identity.releasedVersions.map { it.mostRecentSnapshot.version }
}

val copyTestedVersionsInfo = tasks.register<Copy>("copyTestedVersionsInfo") {
    group = "build"
    description = "Copies the AGP, Kotlin and smoke-tested plugin version properties into the generated resources directory."
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/kotlin-versions.properties"))
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/smoke-tested-plugins.properties"))
    into(layout.buildDirectory.dir("generated-resources/tested-versions"))
}

val generateLanguageAnnotations = tasks.register<GenerateLanguageAnnotations>("generateLanguageAnnotations") {
    group = "build"
    description = "Generates the Groovy language annotations used by integration test fixtures."
    classpath.from(configurations.integTestDistributionRuntimeClasspath)
    packageName = "org.gradle.integtests.fixtures"
    destDir = layout.buildDirectory.dir("generated/sources/language-annotations/groovy/main")
}

sourceSets.main {
    groovy.srcDir(generateLanguageAnnotations.flatMap { it.destDir })
    output.dir(mapOf("builtBy" to prepareVersionsInfo), allReleasedVersionsDir)
    output.dir(copyTestedVersionsInfo)
}

@CacheableTask
abstract class PrepareVersionsInfo : DefaultTask() {

    @get:OutputFile
    abstract val destFile: RegularFileProperty

    @get:Input
    abstract val mostRecent: Property<String>

    @get:Input
    abstract val versions: Property<String>

    @get:Input
    abstract val mostRecentSnapshot: Property<String>

    @TaskAction
    fun prepareVersions() {
        val properties = Properties()
        properties["mostRecent"] = mostRecent.get()
        properties["mostRecentSnapshot"] = mostRecentSnapshot.get()
        properties["versions"] = versions.get()
        gradlebuild.basics.util.ReproduciblePropertiesWriter.store(properties, destFile.get().asFile)
    }
}
