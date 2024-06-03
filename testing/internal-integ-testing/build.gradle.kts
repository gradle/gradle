import gradlebuild.integrationtests.tasks.GenerateLanguageAnnotations
import java.util.Properties

plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for integration tests, internal use only"

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(project(":base-services")) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(project(":build-cache-base"))
    api(project(":build-operations"))
    api(project(":concurrent"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":hashing"))
    api(project(":internal-testing")) {
        because("Part of the public API")
    }
    api(project(":java-language-extensions"))
    api(project(":jvm-services")) {
        because("Exposing jvm metadata via AvailableJavaHomes")
    }
    api(project(":launcher"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":native"))
    api(project(":persistent-cache"))
    api(project(":problems-api"))
    api(project(":process-services"))

    api(testFixtures(project(":core"))) {
        because("HttpServer leaks PortAllocator to spock AST transformer")
    }

    api(libs.gson)
    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.guava)
    api(libs.hamcrestCore)
    api(libs.hamcrest)
    api(libs.jettyWebApp) {
        because("Part of the public API via HttpServer")
    }
    api(libs.jansi)
    api(libs.jettySecurity)
    api(libs.jettyServer)
    api(libs.jettyUtil)
    api(libs.jgit) {
        because("Some tests require a git reportitory - see AbstractIntegrationSpec.initGitDir(")
    }
    api(libs.jsr305)
    api(libs.junit) {
        because("Part of the public API, used by spock AST transformer")
    }
    api(libs.mavenResolverApi) {
        because("For ApiMavenResolver. API we interact with to resolve Maven graphs & artifacts")
    }
    api(libs.samplesCheck) {
        exclude(module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    api(libs.samplesDiscovery)
    api(libs.servletApi)
    api(libs.slf4jApi)
    api(libs.socksProxy)
    api(libs.spock) {
        because("Part of the public API")
    }

    implementation(projects.serviceProvider)
    implementation(project(":base-services-groovy"))
    implementation(project(":build-cache"))
    implementation(project(":build-events"))
    implementation(project(":build-option"))
    implementation(project(":build-state"))
    implementation(project(":cli"))
    implementation(project(":daemon-protocol"))
    implementation(project(":daemon-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":enterprise-logging"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(projects.io)
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":platform-jvm"))
    implementation(project(":serialization"))
    implementation(project(":time"))
    implementation(projects.toolchainsJvmShared)
    implementation(project(":build-process-services"))

    implementation(testFixtures(project(":build-operations")))

    implementation(libs.ansiControlSequenceUtil)
    implementation(libs.commonsCompress)
    implementation(libs.commonsLang)
    implementation(libs.commonsLang3)
    implementation(libs.commonsIo)
    implementation(libs.groovyAnt)
    implementation(libs.groovyJson)
    implementation(libs.httpcore)
    implementation(libs.inject)
    implementation(libs.ivy)
    implementation(libs.jcifs)
    implementation(libs.jetty)
    implementation(libs.jettyServlet)
    implementation(libs.littleproxy)
    implementation(libs.mavenResolverSupplier) {
        because("For ApiMavenResolver. Wires together implementation for maven-resolver-api")
    }
    implementation(libs.maven3ResolverProvider) {
        because("For ApiMavenResolver. Provides MavenRepositorySystemUtils")
    }
    implementation(libs.nativePlatform)
    implementation(libs.netty)
    implementation(libs.opentest4j)
    // we depend on both: sshd platforms and libraries
    implementation(libs.sshdCore)
    implementation(platform(libs.sshdCore))
    implementation(libs.sshdScp)
    implementation(platform(libs.sshdScp))
    implementation(libs.sshdSftp)
    implementation(platform(libs.sshdSftp))

    compileOnly(project(":configuration-cache")) {
        because("""Fixes:
            compiler message file broken: key=compiler.misc.msg.bug arguments=11.0.21, {1}, {2}, {3}, {4}, {5}, {6}, {7}
            java.lang.AssertionError: typeSig ERROR""")
    }

    runtimeOnly(libs.mavenResolverImpl) {
        because("For ApiMavenResolver. Implements maven-resolver-api")
    }
    runtimeOnly(libs.mavenResolverConnectorBasic) {
        because("For ApiMavenResolver. To use resolver transporters")
    }
    runtimeOnly(libs.mavenResolverTransportFile) {
        because("For ApiMavenResolver. To resolve file:// URLs")
    }
    runtimeOnly(libs.mavenResolverTransportHttp) {
        because("For ApiMavenResolver. To resolve http:// URLs")
    }

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
