plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
}

description = """Performance tests for the build scan plugin
    | Run as part of the GE pipeline.
    | """.trimMargin()

dependencies {
    performanceTestImplementation(projects.baseServices)
    performanceTestImplementation(projects.internalTesting)

    performanceTestCompileOnly(projects.internalIntegTesting)
    performanceTestCompileOnly(projects.internalPerformanceTesting)

    performanceTestImplementation(libs.gradleProfiler)

    testFixturesApi(projects.baseServices)

    testFixturesApi(libs.commonsIo)

    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.internalPerformanceTesting)
    testFixturesImplementation(projects.logging)

    testFixturesImplementation(libs.groovyJson)

    performanceTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("so that all Gradle features are available")
    }
}

performanceTest.registerTestProject<gradlebuild.performance.generator.tasks.JvmProjectGeneratorTask>("javaProject") {
    dependencyGraph.run {
        size = 200
        depth = 5
        useSnapshotVersions = false // snapshots should not have a build scan specific performance impact
    }

    buildSrcTemplate = "buildsrc-plugins"
    setProjects(50)
    sourceFiles = 200
    testSourceFiles = 50 // verbose tests are time consuming
    filesPerPackage = 5
    linesOfCodePerSourceFile = 150
    numberOfScriptPlugins = 30
    rootProjectTemplates = listOf("root")
    subProjectTemplates = listOf("project-with-source")
    templateArgs = mapOf(
        "fullTestLogging" to true,
        "failedTests" to true,
        "projectDependencies" to true,
        "manyPlugins" to true,
        "manyScripts" to true
    )
    daemonMemory = "4096m"
    maxWorkers = 4
    doLast {
        File(destDir, "build.gradle").appendText("""
// gradle-profiler doesn't support expectFailure
subprojects {
    afterEvaluate {
        test.ignoreFailures = true
    }
}
""")
    }
}

tasks.withType<gradlebuild.performance.tasks.PerformanceTest>().configureEach {
    environment("GRADLE_INTERNAL_REPO_URL", System.getenv("GRADLE_INTERNAL_REPO_URL"))
    environment("GRADLE_INTERNAL_REPO_USERNAME", System.getenv("GRADLE_INTERNAL_REPO_USERNAME"))
    environment("GRADLE_INTERNAL_REPO_PASSWORD", System.getenv("GRADLE_INTERNAL_REPO_PASSWORD"))

    reportGeneratorClass = "org.gradle.performance.results.BuildScanReportGenerator"

    val projectRootDir = project.rootDir
    val pluginInfoDir = project.providers.gradleProperty("enterprisePluginInfoDir")
        .orElse(projectRootDir.resolve("incoming").path)
        .map { projectRootDir.resolve(it) }

    // Provides a system property required by `AbstractBuildScanPluginPerformanceTest`
    jvmArgumentProviders += DevelocityPluginInfoDirPropertyProvider(pluginInfoDir)
}

internal
class DevelocityPluginInfoDirPropertyProvider(@InputFiles @PathSensitive(PathSensitivity.RELATIVE) val pluginInfoDir: Provider<File>) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dorg.gradle.performance.develocity.plugin.infoDir=${pluginInfoDir.get().path}")
}
