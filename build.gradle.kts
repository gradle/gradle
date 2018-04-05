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

import org.gradle.api.internal.GradleInternal
import org.gradle.build.Install
import org.gradle.gradlebuild.ProjectGroups
import org.gradle.modules.PatchExternalModules
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.ProjectGroups.implementationPluginProjects
import org.gradle.gradlebuild.ProjectGroups.javaProjects
import org.gradle.gradlebuild.ProjectGroups.pluginProjects
import org.gradle.gradlebuild.ProjectGroups.publishedProjects
import java.util.concurrent.Callable

plugins {
    `java-base`
    id("gradlebuild.build-types")
    id("gradlebuild.ci-reporting")
    // TODO Apply this plugin in the BuildScanConfigurationPlugin once binary plugins can apply plugins via the new plugin DSL
    // We have to apply it here at the moment, so that when the build scan plugin is auto-applied via --scan can detect that
    // the plugin has been already applied. For that the plugin has to be applied with the new plugin DSL syntax.
    id("com.gradle.build-scan")
}

defaultTasks("assemble")
base.archivesBaseName = "gradle"

buildTypes {
    create("sanityCheck") {
        tasks("classes", "doc:checkstyleApi", "codeQuality", "docs:check", "distribution:checkBinaryCompatibility", "javadocAll")
        projectProperties(mapOf("ignoreIncomingBuildReceipt" to true))
    }

    // Used by the first phase of the build pipeline, running only last version on multiversion - tests
    create("quickTest") {
        tasks("test", "integTest", "crossVersionTest")
    }

    // Used for builds to run all tests, but not necessarily on all platforms
    create("fullTest") {
        tasks("test", "forkingIntegTest", "forkingCrossVersionTest")
        projectProperties(mapOf("testAllVersions" to true))
    }

    // Used for builds to test the code on certain platforms
    create("platformTest") {
        tasks("test", "forkingIntegTest", "forkingCrossVersionTest")
        projectProperties(mapOf("testAllVersions" to true, "testAllPlatforms" to true))
    }

    // Tests not using the daemon mode
    create("noDaemonTest") {
        tasks("noDaemonIntegTest")
        projectProperties(mapOf("useAllDistribution" to true))
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

    // Used for cross version tests on CI
    create("allVersionsCrossVersionTest") {
        tasks("allVersionsCrossVersionTests")
    }

    create("quickFeedbackCrossVersionTest") {
        tasks("quickFeedbackCrossVersionTests")
    }

    // Used to build production distros and smoke test them
    create("packageBuild") {
        tasks("verifyIsProductionBuildEnvironment", "clean", "buildDists", "distributions:integTest")
    }

    // Used to build production distros and smoke test them
    create("promotionBuild") {
        tasks("verifyIsProductionBuildEnvironment", "clean", "docs:check", "buildDists", "distributions:integTest", "uploadArchives")
    }

    create("soakTest") {
        tasks("soak:soakTest")
        projectProperties(mapOf("testAllVersions" to true))
    }
}

allprojects {
    group = "org.gradle"

    repositories {
        maven(url = "https://repo.gradle.org/gradle/libs-releases")
        maven(url = "https://repo.gradle.org/gradle/libs-milestones")
        maven(url = "https://repo.gradle.org/gradle/libs-snapshots")
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


subprojects {
    version = rootProject.version

    if (project in javaProjects) {
        apply(plugin = "gradlebuild.java-projects")
        apply(plugin = "gradlebuild.dependencies-metadata-rules")
    }

    if (project in publishedProjects) {
        apply(plugin = "gradlebuild.publish-public-libraries")
    }

    apply(from = "$rootDir/gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")
    apply(plugin = "gradlebuild.task-properties-validation")
    apply(plugin = "gradlebuild.test-files-cleanup")
}


configurations {
    "coreRuntime" {
        usage(Usage.JAVA_RUNTIME)
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
    }
    "coreRuntimeExtensions" {
        usage(Usage.JAVA_RUNTIME)
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
    }
    "externalModules" {
        isVisible = false
    }
    // Configuration used to resolve external modules before patching them with versions from core runtime
    "externalModulesRuntime" {
        isVisible = false
        extendsFrom(configurations["coreRuntime"])
        extendsFrom(configurations["externalModules"])
    }
    // Combines the 'coreRuntime' with the patched external module jars
    "runtime" {
        isVisible = false
        extendsFrom(configurations["coreRuntime"])
    }
    "gradlePlugins" {
        isVisible = false
    }
    "testRuntime" {
        extendsFrom(configurations["runtime"])
        extendsFrom(configurations["gradlePlugins"])
    }

    all {
        usage(Usage.JAVA_RUNTIME)
    }
}

extra.set("allTestRuntimeDependencies", configurations["testRuntime"].allDependencies)

val patchedExternalModulesDir = File(buildDir, "external/files")
val patchedExternalModules = files(Callable { fileTree(patchedExternalModulesDir).files.sorted() })
patchedExternalModules.builtBy("patchExternalModules")

dependencies {
    "externalModules"("org.gradle:gradle-kotlin-dsl:${BuildEnvironment.gradleKotlinDslVersion}")
    "externalModules"("org.gradle:gradle-kotlin-dsl-tooling-builders:${BuildEnvironment.gradleKotlinDslVersion}")
    "coreRuntime"(project(":launcher"))
    "coreRuntime"(project(":runtimeApiInfo"))
    "runtime"(project(":wrapper"))
    "runtime"(project(":installationBeacon"))
    "runtime"(patchedExternalModules)
    pluginProjects.forEach { "gradlePlugins"(it) }
    implementationPluginProjects.forEach { "gradlePlugins"(it) }
    "gradlePlugins"(project(":workers"))
    "gradlePlugins"(project(":dependencyManagement"))
    "gradlePlugins"(project(":testKit"))

    "coreRuntimeExtensions"(project(":dependencyManagement")) //See: DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES
    "coreRuntimeExtensions"(project(":pluginUse"))
    "coreRuntimeExtensions"(project(":workers"))
    "coreRuntimeExtensions"(patchedExternalModules)
}

extra.set("allCoreRuntimeExtensions", configurations["coreRuntimeExtensions"].allDependencies)

tasks.create<PatchExternalModules>("patchExternalModules") {
    allModules = configurations["externalModulesRuntime"]
    coreModules = configurations["coreRuntime"]
    modulesToPatch = configurations["externalModules"]
    destination = patchedExternalModulesDir
}

evaluationDependsOn(":distributions")

tasks.create<Install>("install") {
    description = "Installs the minimal distribution into directory ${project.property("gradle_installPath")}"
    group = "build"
    with(project(":distributions").property("binDistImage") as CopySpec)
    installDirPropertyName = "gradle_installPath"
}

tasks.create<Install>("installAll") {
    description = "Installs the full distribution into directory ${project.property("gradle_installPath")}"
    group = "build"
    with(project(":distributions").property("allDistImage") as CopySpec)
    installDirPropertyName = "gradle_installPath"
}

afterEvaluate {
    if (gradle.startParameter.isBuildCacheEnabled()) {
        rootProject.availableJavaInstallations.validateBuildCacheConfiguration((gradle as GradleInternal).settings.buildCache)
    }
}

fun Configuration.usage(named: String) =
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(named))
