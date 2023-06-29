import gradlebuild.basics.accessors.groovy
import gradlebuild.integrationtests.tasks.GenerateLanguageAnnotations
import java.util.Properties

plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for integration tests, internal use only"

dependencies {
    api(libs.jettyWebApp) {
        because("Part of the public API via HttpServer")
    }
    api(libs.spock) {
        because("Part of the public API")
    }
    api(project(":internal-testing")) {
        because("Part of the public API")
    }
    api(libs.junit) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(project(":base-services")) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(project(":jvm-services")) {
        because("Exposing jvm metadata via AvailableJavaHomes")
    }

    implementation(project(":enterprise-operations"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":cli"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":resources"))
    implementation(project(":build-cache"))
    implementation(project(":persistent-cache"))
    implementation(project(":dependency-management"))
    implementation(project(":configuration-cache"))
    implementation(project(":launcher"))
    implementation(project(":build-events"))
    implementation(project(":build-option"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyDatetime)
    implementation(libs.groovyJson)
    implementation(libs.groovyXml)
    implementation(libs.nativePlatform)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.jetty)
    implementation(libs.jettySecurity)

    implementation(libs.littleproxy)
    implementation(libs.socksProxy)
    implementation(libs.gcs)
    implementation(libs.inject)
    implementation(libs.commonsHttpclient)
    implementation(libs.joda)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonDatabind)
    implementation(libs.ivy)
    implementation(libs.commonsCompress)
    implementation(libs.jgit) {
        because("Some tests require a git reportitory - see AbstractIntegrationSpec.initGitDir(")
    }
    implementation(libs.jetbrainsAnnotations) {
        because("Generated language annotations for spock tests")
    }

    // we depend on both: sshd platforms and libraries
    implementation(libs.sshdCore)
    implementation(platform(libs.sshdCore))
    implementation(libs.sshdScp)
    implementation(platform(libs.sshdScp))
    implementation(libs.sshdSftp)
    implementation(platform(libs.sshdSftp))

    implementation(libs.gson)
    implementation(libs.joda)
    implementation(libs.jsch)
    implementation(libs.jcifs)
    implementation(libs.jansi)
    implementation(libs.ansiControlSequenceUtil)
    implementation(libs.mina)
    implementation(libs.samplesCheck) {
        exclude(module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    implementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

val prepareVersionsInfo = tasks.register<PrepareVersionsInfo>("prepareVersionsInfo") {
    destFile = layout.buildDirectory.file("generated-resources/all-released-versions/all-released-versions.properties")
    versions = moduleIdentity.releasedVersions.map {
        it.allPreviousVersions.joinToString(" ") { it.version }
    }
    mostRecent = moduleIdentity.releasedVersions.map { it.mostRecentRelease.version }
    mostRecentSnapshot = moduleIdentity.releasedVersions.map { it.mostRecentSnapshot.version }
}

val copyTestedVersionsInfo by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
    from(rootProject.layout.projectDirectory.file("gradle/dependency-management/kotlin-versions.properties"))
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
