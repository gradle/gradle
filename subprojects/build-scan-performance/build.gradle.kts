plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-test")
}

dependencies {
    testFixturesApi(project(":internal-performance-testing"))
    testFixturesApi(libs.commonsIo)
    testFixturesApi(project(":base-services"))
    testFixturesImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    performanceTestDistributionRuntimeOnly(project(":distributions-full")) {
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
    systemProperties["incomingArtifactDir"] = "$rootDir/incoming/"

    environment("ARTIFACTORY_USERNAME", System.getenv("ARTIFACTORY_USERNAME"))
    environment("ARTIFACTORY_PASSWORD", System.getenv("ARTIFACTORY_PASSWORD"))

    reportGeneratorClass.set("org.gradle.performance.results.BuildScanReportGenerator")
}
