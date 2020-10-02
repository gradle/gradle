import gradlebuild.performance.generator.tasks.JvmProjectGeneratorTask
import gradlebuild.performance.tasks.PerformanceTest

/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

performanceTest.registerTestProject<JvmProjectGeneratorTask>("javaProject") {
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

tasks.withType<PerformanceTest>().configureEach {
    systemProperties["incomingArtifactDir"] = "$rootDir/incoming/"

    environment("ARTIFACTORY_USERNAME", System.getenv("ARTIFACTORY_USERNAME"))
    environment("ARTIFACTORY_PASSWORD", System.getenv("ARTIFACTORY_PASSWORD"))

    reportGeneratorClass.set("org.gradle.performance.results.BuildScanReportGenerator")
}
