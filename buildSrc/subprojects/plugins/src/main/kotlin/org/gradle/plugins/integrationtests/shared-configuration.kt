package org.gradle.plugins.integrationtests

import accessors.eclipse
import accessors.groovy
import accessors.idea
import accessors.java
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.compile.AvailableJavaInstallations
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.testing.IntegrationTest

enum class TestType(val prefix: String, val modes: List<String>, val libRepoRequired: Boolean) {
    INTEGRATION("integ", listOf("embedded", "forking", "noDaemon", "parallel"),  false),
    CROSSVERSION("crossVersion", listOf("embedded", "forking"), true)
}

internal
fun Project.addDependenciesAndConfigurations(testType: TestType) {
    val prefix = testType.prefix
    configurations {
        getByName("${prefix}TestCompile") { extendsFrom(configurations["testCompile"]) }
        getByName("${prefix}TestRuntime") { extendsFrom(configurations["testRuntime"]) }
        getByName("${prefix}TestImplementation") { extendsFrom(configurations["testImplementation"]) }
        getByName("${prefix}TestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
        getByName("partialDistribution") { extendsFrom(configurations["${prefix}TestRuntimeClasspath"]) }
    }

    dependencies {
        "${prefix}TestCompile"(project(":internalIntegTesting"))

        //so that implicit help tasks are available:
        "${prefix}TestRuntime"(project(":diagnostics"))

        //So that the wrapper and init task are added when integTests are run via commandline
        "${prefix}TestRuntime"(project(":buildInit"))
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
    (listOf("${prefix}Test" to defaultExecuter) + testType.modes.map { "${it}${prefix.capitalize()}Test" to it }).
        forEach { createTestTask("${it.first}", it.second, sourceSet, testType)
        }
    tasks["check"].dependsOn("${prefix}Test")
}

private
fun Project.createTestTask(name: String, executer: String, sourceSet: SourceSet, testType: TestType): IntegrationTest {

    return tasks.create<IntegrationTest>(name) {
        addBaseConfigurationForIntegrationAndCrossVersionTestTasks(currentTestJavaVersion)
        description = "Runs ${testType.prefix} with $executer executer"
        systemProperties["org.gradle.integtest.executer"] = executer
        addDebugProperties()
        testClassesDirs = sourceSet.output.classesDirs
        classpath = sourceSet.runtimeClasspath
        libsRepository.required = testType.libRepoRequired
    }
}

private
fun IntegrationTest.addBaseConfigurationForIntegrationAndCrossVersionTestTasks(currentTestJavaVersion: JavaVersion) {
    group = "verification"
    exclude(testExcluder.excludesForJavaVersion(currentTestJavaVersion))
}

private fun IntegrationTest.addDebugProperties() {
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
    val compile = configurations.getByName("${prefix}TestCompileClasspath")
    val runtime = configurations.getByName("${prefix}TestRuntimeClasspath")
    val sourceSet = java.sourceSets.getByName("${prefix}Test")

    // We apply lazy as we don't want to depend on the order
    plugins.withType<IdeaPlugin> {
        idea {
            module {
                testSourceDirs = testSourceDirs + sourceSet.groovy.srcDirs + sourceSet.resources.srcDirs
                scopes["TEST"]!!["plus"]!!.apply {
                    add(compile)
                    add(runtime)
                }
            }
        }
    }

    plugins.withType<EclipsePlugin> {
        eclipse {
            classpath.plusConfigurations.apply {
                add(compile)
                add(runtime)
            }
        }
    }
}

internal
val testExcluder = TestExcluder(excludedTests)

internal
val Project.currentTestJavaVersion
    get() = rootProject.the<AvailableJavaInstallations>().javaInstallationForTest.javaVersion


internal
class TestExcluder(excludeInputs: List<Pair<String, List<JavaVersion>>>) {
    val excludeRules = exclude(excludeInputs)

    private
    fun exclude(pairs: List<Pair<String, List<JavaVersion>>>): Map<JavaVersion, Set<String>> {
        val excludes = mutableMapOf<JavaVersion, MutableSet<String>>()
        pairs.forEach { nameVersionPair ->
            nameVersionPair.second.forEach {
                val excludesForVersion = excludes.get(it) ?: mutableSetOf()
                excludesForVersion.add("**/*${nameVersionPair.first}*")
                excludes.put(it, excludesForVersion)
            }
        }
        return excludes
    }

    fun excludesForJavaVersion(version: JavaVersion) = excludeRules[version] ?: emptySet()
}

