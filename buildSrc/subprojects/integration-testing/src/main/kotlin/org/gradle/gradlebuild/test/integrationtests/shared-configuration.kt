package org.gradle.gradlebuild.test.integrationtests

import accessors.groovy
import accessors.java
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.gradlebuild.versioning.buildVersion


enum class TestType(val prefix: String, val executers: List<String>, val libRepoRequired: Boolean) {
    INTEGRATION("integ", listOf("embedded", "forking", "noDaemon", "parallel", "instant", "vfsRetention"), false),
    CROSSVERSION("crossVersion", listOf("embedded", "forking"), true)
}


internal
fun Project.addDependenciesAndConfigurations(testType: TestType) {
    val prefix = testType.prefix
    configurations {
        getByName("${prefix}TestImplementation") { extendsFrom(configurations["testImplementation"]) }
        getByName("${prefix}TestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
    }

    dependencies {
        "${prefix}TestImplementation"(project(":internalIntegTesting"))

        // so that implicit help tasks are available:
        "${prefix}TestRuntimeOnly"(project(":diagnostics"))

        // So that the wrapper and init task are added when integTests are run via commandline
        "${prefix}TestRuntimeOnly"(project(":buildInit"))
    }
}


internal
fun Project.addSourceSet(testType: TestType): SourceSet {
    val prefix = testType.prefix
    val main by java.sourceSets.getting
    return java.sourceSets.create("${prefix}Test") {
        compileClasspath += main.output
        runtimeClasspath += main.output
    }
}


internal
fun Project.createTasks(sourceSet: SourceSet, testType: TestType) {
    val prefix = testType.prefix
    val defaultExecuter = project.findProperty("defaultIntegTestExecuter") as? String ?: "embedded"

    // For all of the other executers, add an executer specific task
    testType.executers.forEach { executer ->
        val taskName = "$executer${prefix.capitalize()}Test"
        createTestTask(taskName, executer, sourceSet, testType, Action {
            if (testType == TestType.CROSSVERSION) {
                // the main crossVersion test tasks always only check the latest version,
                // for true multi-version testing, we set up a test task per Gradle version,
                // (see CrossVersionTestsPlugin).
                systemProperties["org.gradle.integtest.versions"] = "default"
            }
        })
    }
    // Use the default executer for the simply named task. This is what most developers will run when running check
    val testTask = createTestTask(prefix + "Test", defaultExecuter, sourceSet, testType, Action {})
    // Create a variant of the test suite to force realization of component metadata
    if (testType == TestType.INTEGRATION) {
        createTestTask(prefix + "ForceRealizeTest", defaultExecuter, sourceSet, testType, Action {
            systemProperties["org.gradle.integtest.force.realize.metadata"] = "true"
        })
    }
    tasks.named("check").configure { dependsOn(testTask) }
}


internal
fun Project.createTestTask(name: String, executer: String, sourceSet: SourceSet, testType: TestType, extraConfig: Action<IntegrationTest>): TaskProvider<IntegrationTest> =
    tasks.register(name, IntegrationTest::class) {
        project.bucketProvider().configureTest(this, sourceSet, testType)
        description = "Runs ${testType.prefix} with $executer executer"
        systemProperties["org.gradle.integtest.executer"] = executer
        addDebugProperties()
        testClassesDirs = sourceSet.output.classesDirs
        classpath = sourceSet.runtimeClasspath
        libsRepository.required = testType.libRepoRequired
        extraConfig.execute(this)
    }


/**
 * Distributed test requires all dependencies to be declared
 */
fun Project.integrationTestUsesSampleDir(vararg sampleDirs: String) {
    tasks.withType<IntegrationTest>() {
        systemProperty("declaredSampleInputs", sampleDirs.joinToString(";"))
        inputs.files(rootProject.files(sampleDirs))
            .withPropertyName("autoTestedSamples")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


fun Project.integrationTestUsesKotlinDslPlugins() {
    tasks.withType<IntegrationTest>().configureEach {
        inputs.dir(project(":kotlinDslPlugins").buildDir.resolve("repository"))
            .withPropertyName("localRepository")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


fun Project.integrationTestUsesToolingApiFatJar() {
    tasks.withType<IntegrationTest>().configureEach {
        inputs.file(project(":toolingApi").buildDir.resolve("shaded-jar/gradle-tooling-api-shaded-${rootProject.buildVersion.baseVersion}.jar"))
            .withPropertyName("fatToolingApiJar")
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withNormalizer(ClasspathNormalizer::class)
    }
}


fun Project.integrationTestUsesToolingApiJar() {
    tasks.withType<IntegrationTest>().configureEach {
        inputs.dir(rootProject.buildDir.resolve("repo"))
            .withPropertyName("toolingApiJarRepo")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


// ""/bin/all/docs/src
fun Project.integrationTestUsesDistribution(type: String = "bin") {
    tasks.withType<IntegrationTest>().configureEach {
        inputs.file(rootProject.buildDir.resolve("distributions/gradle-${rootProject.buildVersion.baseVersion}${if (type == "") ".jar" else "-$type.zip"}"))
            .withPropertyName("distribution")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


private
fun IntegrationTest.addDebugProperties() {
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.debug")) {
        systemProperties["org.gradle.integtest.debug"] = "true"
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.verbose")) {
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.launcher.debug")) {
        systemProperties["org.gradle.integtest.launcher.debug"] = "true"
    }
}


internal
fun Project.configureIde(testType: TestType) {
    val prefix = testType.prefix
    val sourceSet = java.sourceSets.getByName("${prefix}Test")

    // We apply lazy as we don't want to depend on the order
    plugins.withType<IdeaPlugin> {
        with(model) {
            module {
                testSourceDirs = testSourceDirs + sourceSet.java.srcDirs
                testSourceDirs = testSourceDirs + sourceSet.groovy.srcDirs
                testResourceDirs = testResourceDirs + sourceSet.resources.srcDirs
            }
        }
    }
}
