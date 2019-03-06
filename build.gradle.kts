/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.build.BuildReceipt
import org.gradle.build.Install
import org.gradle.gradlebuild.ProjectGroups.implementationPluginProjects
import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.ProjectGroups.pluginProjects
import org.gradle.gradlebuild.ProjectGroups.publicJavaProjects
import org.gradle.gradlebuild.ProjectGroups.publishedProjects
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiAggregateReportTask
import org.gradle.gradlebuild.buildquality.incubation.IncubatingApiReportTask

plugins {
    `java-base`
    gradlebuild.`build-types`
    gradlebuild.`ci-reporting`
    // TODO Apply this plugin in the BuildScanConfigurationPlugin once binary plugins can apply plugins via the new plugin DSL
    // We have to apply it here at the moment, so that when the build scan plugin is auto-applied via --scan can detect that
    // the plugin has been already applied. For that the plugin has to be applied with the new plugin DSL syntax.
    com.gradle.`build-scan`
    id("org.gradle.ci.tag-single-build") version("0.55")
}

defaultTasks("assemble")

base.archivesBaseName = "gradle"

buildTypes {
    create("compileAllBuild") {
        tasks(":createBuildReceipt", "compileAll", ":docs:distDocs")
        projectProperties("ignoreIncomingBuildReceipt" to true)
    }

    create("sanityCheck") {
        tasks(
            "classes", "doc:checkstyleApi", "codeQuality", "allIncubationReportsZip",
            "docs:check", "distribution:checkBinaryCompatibility", "javadocAll",
            "architectureTest:test", "toolingApi:toolingApiShadedJar")
    }

    // Used by the first phase of the build pipeline, running only last version on multiversion - tests
    create("quickTest") {
        tasks("test", "integTest", "crossVersionTest")
    }

    // Used for builds to run all tests
    create("fullTest") {
        tasks("test", "forkingIntegTest", "forkingCrossVersionTest")
        projectProperties("testAllVersions" to true)
    }

    // Used for builds to test the code on certain platforms
    create("platformTest") {
        tasks("test", "forkingIntegTest", "forkingCrossVersionTest")
        projectProperties("testPartialVersions" to true)
    }

    // Tests not using the daemon mode
    create("noDaemonTest") {
        tasks("noDaemonIntegTest")
        projectProperties("useAllDistribution" to true)
    }

    // Run the integration tests using the parallel executer
    create("parallelTest") {
        tasks("parallelIntegTest")
    }

    create("performanceTests") {
        tasks("performance:performanceTest")
    }

    create("performanceExperiments") {
        tasks("performance:performanceExperiments")
    }

    create("fullPerformanceTests") {
        tasks("performance:fullPerformanceTest")
    }

    create("distributedPerformanceTests") {
        tasks("performance:distributedPerformanceTest")
    }

    create("distributedPerformanceExperiments") {
        tasks("performance:distributedPerformanceExperiment")
    }

    create("distributedFullPerformanceTests") {
        tasks("performance:distributedFullPerformanceTest")
    }

    create("distributedFlakinessDetections") {
        tasks("performance:distributedFlakinessDetection")
    }

    // Used for cross version tests on CI
    create("allVersionsCrossVersionTest") {
        tasks("allVersionsCrossVersionTests", "integMultiVersionTest")
        projectProperties("testAllVersions" to true)
    }

    create("quickFeedbackCrossVersionTest") {
        tasks("quickFeedbackCrossVersionTests")
    }

    // Used to build production distros and smoke test them
    create("packageBuild") {
        tasks(
            "verifyIsProductionBuildEnvironment", "clean", "buildDists",
            "distributions:integTest")
    }

    // Used to build production distros and smoke test them
    create("promotionBuild") {
        tasks(
            "verifyIsProductionBuildEnvironment", "clean", "docs:check",
            "buildDists", "distributions:integTest", "uploadArchives")
    }

    create("soakTest") {
        tasks("soak:soakTest")
        projectProperties("testAllVersions" to true)
    }

    // Used to run the dependency management engine in "force component realization" mode
    create("forceRealizeDependencyManagementTest") {
        tasks("integForceRealizeTest")
    }
}

allprojects {
    group = "org.gradle"

    repositories {
        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
        }
        maven {
            name = "kotlinx"
            url = uri("https://kotlin.bintray.com/kotlinx/")
        }
        maven {
            name = "kotlin-eap"
            url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
        }
        maven {
            name = "sonatype-snapshots"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
            content {
                includeGroup("org.openjdk.jmc")
            }
        }
    }

    // patchExternalModules lives in the root project - we need to activate normalization there, too.
    normalization {
        runtimeClasspath {
            ignore("org/gradle/build-receipt.properties")
        }
    }
}

apply(plugin = "gradlebuild.cleanup")
apply(plugin = "gradlebuild.available-java-installations")
apply(plugin = "gradlebuild.buildscan")
apply(from = "gradle/versioning.gradle")
apply(from = "gradle/dependencies.gradle")
apply(plugin = "gradlebuild.minify")
apply(from = "gradle/testDependencies.gradle")
apply(plugin = "gradlebuild.wrapper")
apply(plugin = "gradlebuild.ide")
apply(plugin = "gradlebuild.no-resolution-at-configuration")
apply(plugin = "gradlebuild.update-versions")
apply(plugin = "gradlebuild.dependency-vulnerabilities")
apply(plugin = "gradlebuild.add-verify-production-environment-task")

allprojects {
    apply(plugin = "gradlebuild.dependencies-metadata-rules")
}

subprojects {
    version = rootProject.version

    if (project in javaProjects) {
        apply(plugin = "gradlebuild.java-projects")
    }

    if (project in publicJavaProjects) {
        apply(plugin = "gradlebuild.public-java-projects")
    }

    if (project in publishedProjects) {
        apply(plugin = "gradlebuild.publish-public-libraries")
    }

    apply(from = "$rootDir/gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")
    apply(plugin = "gradlebuild.task-properties-validation")
    apply(plugin = "gradlebuild.test-files-cleanup")
}

val runtimeUsage = objects.named(Usage::class.java, Usage.JAVA_RUNTIME)

val coreRuntime by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage)
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}

val coreRuntimeExtensions by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage)
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}

val externalModules by configurations.creating {
    isVisible = false
}

/**
 * Combines the 'coreRuntime' with the patched external module jars
 */
val runtime by configurations.creating {
    isVisible = false
    extendsFrom(coreRuntime)
}

val gradlePlugins by configurations.creating {
    isVisible = false
}

val testRuntime by configurations.creating {
    extendsFrom(coreRuntime)
    extendsFrom(gradlePlugins)
}

// TODO: These should probably be all collapsed into a single variant
configurations {
    create("gradleApiMetadataElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "metadata")
    }
}
configurations {
    create("gradleApiRuntimeElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(externalModules)
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "runtime")
    }
}
configurations {
    create("gradleApiCoreElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "core")
    }
}
configurations {
    create("gradleApiCoreExtensionsElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(coreRuntime)
        extendsFrom(coreRuntimeExtensions)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "core-ext")
    }
}
configurations {
    create("gradleApiPluginElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(gradlePlugins)
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "plugins")
    }
}
configurations {
    create("gradleApiReceiptElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Attribute.of("org.gradle.api", String::class.java), "build-receipt")

        // TODO: Update BuildReceipt to retain dependency information by using Provider
        val createBuildReceipt = tasks.named("createBuildReceipt", BuildReceipt::class.java)
        val receiptFile = createBuildReceipt.map {
            it.receiptFile
        }
        outgoing.artifact(receiptFile) {
            builtBy(createBuildReceipt)
        }
    }
}

configurations {
    all {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage)
    }
}

extra["allTestRuntimeDependencies"] = testRuntime.allDependencies

dependencies {

    coreRuntime(project(":launcher"))
    coreRuntime(project(":runtimeApiInfo"))
    coreRuntime(project(":wrapper"))
    coreRuntime(project(":installationBeacon"))
    coreRuntime(project(":kotlinDsl"))

    pluginProjects.forEach { gradlePlugins(it) }
    implementationPluginProjects.forEach { gradlePlugins(it) }

    gradlePlugins(project(":workers"))
    gradlePlugins(project(":dependencyManagement"))
    gradlePlugins(project(":testKit"))

    coreRuntimeExtensions(project(":dependencyManagement")) //See: DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES
    coreRuntimeExtensions(project(":pluginUse"))
    coreRuntimeExtensions(project(":workers"))
    coreRuntimeExtensions(project(":kotlinDslProviderPlugins"))
    coreRuntimeExtensions(project(":kotlinDslToolingBuilders"))

    testRuntime(project(":apiMetadata"))
}

extra["allCoreRuntimeExtensions"] = coreRuntimeExtensions.allDependencies

evaluationDependsOn(":distributions")

val gradle_installPath: Any? = findProperty("gradle_installPath")

tasks.register<Install>("install") {
    description = "Installs the minimal distribution into directory $gradle_installPath"
    group = "build"
    with(distributionImage("binDistImage"))
    installDirPropertyName = ::gradle_installPath.name
}

tasks.register<Install>("installAll") {
    description = "Installs the full distribution into directory $gradle_installPath"
    group = "build"
    with(distributionImage("allDistImage"))
    installDirPropertyName = ::gradle_installPath.name
}

fun distributionImage(named: String) =
        project(":distributions").property(named) as CopySpec

val allIncubationReports = tasks.register<IncubatingApiAggregateReportTask>("allIncubationReports") {
    val allReports = collectAllIncubationReports()
    dependsOn(allReports)
    reports = allReports.associateBy({ it.title.get()}) { it.textReportFile.asFile.get() }
}
tasks.register<Zip>("allIncubationReportsZip") {
    destinationDir = file("$buildDir/reports/incubation")
    baseName = "incubating-apis"
    from(allIncubationReports.get().htmlReportFile)
    from(collectAllIncubationReports().map { it.htmlReportFile })
}

fun Project.collectAllIncubationReports() = subprojects.flatMap { it.tasks.withType(IncubatingApiReportTask::class) }

// Ensure the archives produced are reproducible
allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = Integer.parseInt("0755", 8)
        fileMode = Integer.parseInt("0644", 8)
    }
}
