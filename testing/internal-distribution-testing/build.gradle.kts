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
    api(projects.coreApi)
    api(projects.daemonProtocol)
    api(projects.hashing)
    api(projects.internalTesting)
    api(projects.jvmServices)
    api(projects.persistentCache)
    api(projects.stdlibJavaExtensions)
    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.gson)
    api(libs.guava)
    api(libs.hamcrest)
    api(libs.jettySecurity)
    api(libs.jettyServer)
    api(libs.jettyUtil)
    api(libs.jsr305)
    api(libs.junit)
    api(libs.samplesCheck)
    api(libs.servletApi)
    api(libs.slf4jApi)
    api(libs.spock)

    implementation(projects.baseServicesGroovy)
    implementation(projects.buildProcessServices)
    implementation(projects.buildOperations)
    implementation(projects.clientServices)
    implementation(projects.daemonLogging)
    implementation(projects.dependencyManagement)
    implementation(projects.enterpriseLogging)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.launcher)
    implementation(projects.logging)
    implementation(projects.native)
    implementation(projects.problemsApi)
    implementation(projects.processServices)
    implementation(projects.serviceLookup)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.time)
    implementation(projects.toolingApi)
    implementation(projects.wrapperShared)
    implementation(testFixtures(projects.core))
    implementation(testFixtures(projects.enterpriseLogging))
    implementation(libs.ansiControlSequenceUtil)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.groovyJson)
    implementation(libs.httpcore)
    implementation(libs.ivy)
    implementation(libs.jcifs)
    implementation(libs.jetty)
    implementation(libs.nativePlatform)

    compileOnly(libs.jetbrainsAnnotations)
    compileOnly(libs.jspecify)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

val prepareVersionsInfo = tasks.register<PrepareVersionsInfo>("prepareVersionsInfo") {
    destFile = layout.buildDirectory.file("generated-resources/all-released-versions/all-released-versions.properties")
    versions = gradleModule.identity.releasedVersions.map {
        it.allPreviousVersions.joinToString(" ") { it.version }
    }
    mostRecent = gradleModule.identity.releasedVersions.map { it.mostRecentRelease.version }
    mostRecentSnapshot = gradleModule.identity.releasedVersions.map { it.mostRecentSnapshot.version }
}

val copyTestedVersionsInfo by tasks.registering(Copy::class) {
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/kotlin-versions.properties"))
    from(isolated.rootProject.projectDirectory.file("gradle/dependency-management/smoke-tested-plugins.properties"))
    into(layout.buildDirectory.dir("generated-resources/tested-versions"))
}

val generateLanguageAnnotations by tasks.registering(GenerateLanguageAnnotations::class) {
    classpath.from(configurations.integTestDistributionRuntimeClasspath)
    packageName = "org.gradle.integtests.fixtures"
    destDir = layout.buildDirectory.dir("generated/sources/language-annotations/groovy/main")
}

sourceSets.main {
    groovy.srcDir(generateLanguageAnnotations.flatMap { it.destDir })
    output.dir(prepareVersionsInfo.map { it.destFile.get().asFile.parentFile })
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

packageCycles {
    excludePatterns.add("org/gradle/**")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
