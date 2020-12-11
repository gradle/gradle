import gradlebuild.basics.repoRoot
import gradlebuild.integrationtests.tasks.GenerateLanguageAnnotations
import java.util.Properties

plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    api(libs.jettyWebApp) {
        because("Part of the public API via HttpServer")
    }
    api("org.gradle:jvm-services") {
        because("Exposing jvm metadata via AvailableJavaHomes")
    }

    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:native")
    implementation("org.gradle:logging")
    implementation("org.gradle:cli")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:files")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:resources")
    implementation("org.gradle:build-cache")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:dependency-management")
    implementation("org.gradle:configuration-cache")
    implementation("org.gradle:launcher")
    implementation("org.gradle:internal-testing")
    implementation("org.gradle:build-events")
    implementation("org.gradle:build-option")

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyDatetime)
    implementation(libs.groovyJson)
    implementation(libs.groovyXml)
    implementation(libs.junit)
    implementation(libs.spock)
    implementation(libs.nativePlatform)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.jetty)
    implementation(libs.jettySecurity)

    implementation(libs.littleproxy)
    implementation(libs.gcs)
    implementation(libs.inject)
    implementation(libs.commonsHttpclient)
    implementation(libs.joda)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonDatabind)
    implementation(libs.ivy)
    implementation(libs.ant)
    implementation(libs.jgit) {
        because("Some tests require a git repository - see AbstractIntegrationSpec.initGitDir(")
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
    implementation(libs.sampleCheck) {
        exclude(module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    implementation(testFixtures("org.gradle:core"))

    testRuntimeOnly("org.gradle:distributions-core") {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-core")
}

classycle {
    excludePatterns.add("org/gradle/**")
}

val prepareVersionsInfo = tasks.register<PrepareVersionsInfo>("prepareVersionsInfo") {
    destFile.set(layout.buildDirectory.file("generated-resources/all-released-versions/all-released-versions.properties"))
    versions.set(moduleIdentity.releasedVersions.map {
        it.allPreviousVersions.joinToString(" ") { it.version }
    })
    mostRecent.set(moduleIdentity.releasedVersions.map { it.mostRecentRelease.version })
    mostRecentSnapshot.set(moduleIdentity.releasedVersions.map { it.mostRecentSnapshot.version })
}

val copyAgpVersionsInfo by tasks.registering(Copy::class) {
    // TODO the file 'agp-versions.properties' and the task to generate it should move into this project
    from(repoRoot().file("gradle/dependency-management/agp-versions.properties"))
    into(layout.buildDirectory.dir("generated-resources/agp-versions"))
}

val generateLanguageAnnotations by tasks.registering(GenerateLanguageAnnotations::class) {
    classpath.from(configurations.integTestDistributionRuntimeClasspath)
    packageName.set("org.gradle.integtests.fixtures")
    destDir.set(layout.buildDirectory.dir("generated/sources/language-annotations/groovy/main"))
}

sourceSets.main {
    groovy.srcDir(generateLanguageAnnotations.flatMap { it.destDir })
    output.dir(prepareVersionsInfo.map { it.destFile.get().asFile.parentFile })
    output.dir(copyAgpVersionsInfo)
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
