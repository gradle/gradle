/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r51

import org.gradle.api.Action
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.TimeUnit

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.1')
class ProjectConfigurationProgressEventCrossVersionSpec extends ToolingApiSpecification {

    ProgressEvents events = ProgressEvents.create()

    @Rule
    public BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        file("buildSrc/settings.gradle") << """
            include 'a'
        """
        settingsFile << """
            rootProject.name = 'root'
            include 'b'
            includeBuild 'included'
        """
        file("included/settings.gradle") << """
            include 'c'
        """
    }

    def "reports successful project configuration progress events"() {
        when:
        runBuild("tasks")

        then:
        events.operations.size() == 6
        events.trees == events.operations
        containsSuccessfulProjectConfigurationOperation(":buildSrc", file("buildSrc"), ":")
        containsSuccessfulProjectConfigurationOperation(":buildSrc:a", file("buildSrc"), ":a")
        containsSuccessfulProjectConfigurationOperation(":", projectDir, ":")
        containsSuccessfulProjectConfigurationOperation(":b", projectDir, ":b")
        containsSuccessfulProjectConfigurationOperation(":included", file("included"), ":")
        containsSuccessfulProjectConfigurationOperation(":included:c", file("included"), ":c")
    }

    void containsSuccessfulProjectConfigurationOperation(String displayName, TestFile rootDir, String projectPath) {
        with(events.operation("Configure project $displayName")) {
            assert successful
            assertIsProjectConfiguration()
            assert descriptor.project.projectPath == projectPath
            assert descriptor.project.buildIdentifier.rootDir == rootDir
        }
    }

    def "reports failed project configuration progress events"() {
        given:
        buildFile << """
            throw new GradleException("something went horribly wrong")
        """

        when:
        runBuild("tasks")

        then:
        thrown(BuildException)
        with(events.operation("Configure project :")) {
            failed
            assertIsProjectConfiguration()
            failures.size() == 1
            with(failures[0]) {
                message == "A problem occurred configuring root project 'root'."
                description.contains("GradleException: something went horribly wrong")
            }
        }
    }

    def "does not report project configuration progress events when PROJECT_CONFIGURATION operations are not requested"() {
        when:
        runBuild("tasks", EnumSet.complementOf(EnumSet.of(OperationType.PROJECT_CONFIGURATION)))

        then:
        !events.operations.any { it.projectConfiguration }
    }

    def "reports plugin configuration results for binary plugins"() {
        given:
        file("buildSrc/build.gradle") << """
            allprojects {
                apply plugin: 'java'
            }
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
        """
        file("included/build.gradle") << """
            allprojects {
                apply plugin: 'java'
            }
        """

        when:
        runBuild("tasks")

        then:
        containsPluginApplicationResultsForJavaPlugin(":buildSrc")
        doesNotContainPluginApplicationResultsForJavaPlugin(":buildSrc:a")
        containsPluginApplicationResultsForJavaPlugin(":")
        doesNotContainPluginApplicationResultsForJavaPlugin(":b")
        containsPluginApplicationResultsForJavaPlugin(":included")
        doesNotContainPluginApplicationResultsForJavaPlugin(":included:c")
    }

    def "reports plugin configuration results for script plugins"() {
        given:
        def escapedRootDir = escapeString(projectDir.absolutePath)
        file("script.gradle") << """
            apply plugin: 'java'
        """
        file("buildSrc/build.gradle") << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """
        buildFile << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """
        file("included/build.gradle") << """
            allprojects {
                apply from: "$escapedRootDir/script.gradle"
            }
        """

        when:
        runBuild("tasks")

        then:
        containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":buildSrc", file("buildSrc"))
        doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":buildSrc:a")
        containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":", projectDir)
        doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":b")
        containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":included", file("included"))
        doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(":included:c")
    }

    def "reports plugin configuration results for subprojects"() {
        given:
        file("script.gradle") << """
            apply plugin: 'java'
        """
        file("b/build.gradle") << """
            apply from: "\$rootDir/script.gradle"
        """

        when:
        runBuild("tasks")

        then:
        containsPluginApplicationResultsForJavaPluginAndScriptPlugins(":b", file("b"))
    }

    def "reports plugin configuration results in reliable order"() {
        given:
        file("script.gradle") << """
            apply plugin: 'java'
        """
        file("build.gradle") << """
            apply from: "script.gradle"
        """

        when:
        runBuild("tasks")

        then:
        def plugins = getPluginConfigurationOperationResult(":").getPluginApplicationResults().collect { it.plugin.displayName }
        if (targetVersion >= GradleVersion.version("7.6")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.api.plugins.JvmToolchainsPlugin",
                "org.gradle.jvm-test-suite", "org.gradle.test-suite-base"
            ]
        } else if (targetVersion > GradleVersion.version("7.2")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin",
                "org.gradle.jvm-test-suite", "org.gradle.test-suite-base"
            ]
        } else if (targetVersion >= GradleVersion.version("6.7")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.JvmEcosystemPlugin",
                "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        } else if (targetVersion > GradleVersion.version("5.5.1")) {
            assert plugins == [
                "org.gradle.help-tasks", "org.gradle.build-init", "org.gradle.wrapper",
                "build.gradle", "script.gradle",
                "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                "org.gradle.api.plugins.BasePlugin",
                "org.gradle.language.base.plugins.LifecycleBasePlugin",
                "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        } else {
            assert plugins == [
                    "org.gradle.build-init", "org.gradle.wrapper", "org.gradle.help-tasks",
                    "build.gradle", "script.gradle",
                    "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin",
                    "org.gradle.api.plugins.BasePlugin",
                    "org.gradle.language.base.plugins.LifecycleBasePlugin",
                    "org.gradle.api.plugins.ReportingBasePlugin"
            ]
        }
    }

    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    def "reports plugin configuration results for remote script plugins"() {
        given:
        toolingApi.requireIsolatedUserHome() // So that the script is not cached
        server.start()
        def scriptUri = server.uri("script.gradle")
        server.expect(server.get("script.gradle").send("""
            apply plugin: 'java'
        """))
        file("build.gradle") << """
            apply from: '$scriptUri'
        """

        when:
        runBuild("tasks")

        then:
        def result = getPluginConfigurationOperationResult(":").getPluginApplicationResults().find { it.plugin.displayName == "script.gradle" }
        result.plugin instanceof ScriptPluginIdentifier
        result.plugin.uri == scriptUri
    }

    def "ignores non-project plugins"() {
        given:
        file("build.gradle") << """
            apply(plugin: MyPlugin, to: gradle)
            class MyPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {}
            }
        """

        when:
        runBuild("tasks")

        then:
        getPluginConfigurationOperationResult(":").getPluginApplicationResults().findAll { it.plugin.displayName.contains("MyPlugin") }.empty
    }

    def "includes execution time of project evaluation listener callbacks"() {
        given:
        def sleepMillis = 250
        file("build.gradle") << """
            apply plugin: MyPlugin
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate {
                        ${simulateWork(sleepMillis)}
                    }
                }
            }
        """

        when:
        runBuild("tasks")

        then:
        def pluginResults = getPluginConfigurationOperationResult(":").getPluginApplicationResults()
        def result = pluginResults.find { it.plugin.displayName.contains("MyPlugin") }
        result.totalConfigurationTime >= Duration.ofMillis(sleepMillis)
    }

    def "includes execution time of container callbacks"() {
        given:
        def sleepMillis = 250
        file("build.gradle") << """
            apply plugin: MyPlugin

            configurations {
                foo
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.configurations.all {
                        if (name == 'foo') {
                            ${simulateWork(sleepMillis)}
                        }
                    }
                }
            }
        """

        when:
        runBuild("tasks", EnumSet.of(OperationType.PROJECT_CONFIGURATION))

        then:
        def pluginResults = getPluginConfigurationOperationResult(":").getPluginApplicationResults()
        def result = pluginResults.find { it.plugin.displayName.contains("MyPlugin") }
        result.totalConfigurationTime >= Duration.ofMillis(sleepMillis)
    }

    def "only counts execution time of container callbacks once"() {
        given:
        def sleepDurationMillis = 500
        file("build.gradle") << """
            configurations {
                foo
            }

            apply plugin: MyPlugin

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.configurations.all {
                        if (name == 'foo') {
                            ${simulateWork(sleepDurationMillis)}
                        }
                    }
                }
            }
        """

        when:
        runBuild("tasks", EnumSet.of(OperationType.PROJECT_CONFIGURATION))

        then:
        def pluginResults = getPluginConfigurationOperationResult(":").getPluginApplicationResults()
        def result = pluginResults.find { it.plugin.displayName.contains("MyPlugin") }
        result.totalConfigurationTime >= Duration.ofMillis(sleepDurationMillis)
        result.totalConfigurationTime < Duration.ofMillis(2 * sleepDurationMillis)
    }

    def simulateWork(long durationMillis) {
        """
            def start = org.gradle.internal.time.Time.currentTimeMillis()
            Thread.sleep($durationMillis)
            def elapsed
            while ((elapsed = org.gradle.internal.time.Time.currentTimeMillis() - start) < $durationMillis) {
                Thread.sleep($durationMillis - elapsed)
            }
        """
    }

    void containsPluginApplicationResultsForJavaPluginAndScriptPlugins(String displayName, File buildscriptDir) {
        with(containsPluginApplicationResultsForJavaPlugin(displayName)) {
            def buildScript = pluginApplicationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(buildscriptDir, "build.gradle").toURI() }
            assert buildScript.totalConfigurationTime >= Duration.ZERO
            assert buildScript.plugin.displayName == "build.gradle"
            def scriptPlugin = pluginApplicationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(projectDir, "script.gradle").toURI() }
            assert scriptPlugin.plugin.displayName == "script.gradle"
            assert scriptPlugin.totalConfigurationTime >= Duration.ZERO
        }
    }

    ProjectConfigurationOperationResult containsPluginApplicationResultsForJavaPlugin(String displayName) {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            def javaPluginResult = pluginApplicationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.JavaPlugin" }
            assert javaPluginResult.plugin.pluginId == "org.gradle.java"
            assert javaPluginResult.plugin.displayName == "org.gradle.java"
            assert javaPluginResult.totalConfigurationTime >= Duration.ZERO
            def basePluginResult = pluginApplicationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.BasePlugin" }
            assert basePluginResult.plugin.pluginId == null
            assert basePluginResult.plugin.displayName == "org.gradle.api.plugins.BasePlugin"
            assert basePluginResult.totalConfigurationTime >= Duration.ZERO
        }
        return result
    }

    void doesNotContainPluginApplicationResultsForJavaPluginAndScriptPlugins(String displayName) {
        with(doesNotContainPluginApplicationResultsForJavaPlugin(displayName)) {
            assert pluginApplicationResults.findAll { it.plugin instanceof ScriptPluginIdentifier }.empty
        }
    }

    ProjectConfigurationOperationResult doesNotContainPluginApplicationResultsForJavaPlugin(String displayName) {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            assert pluginApplicationResults.find { it.plugin.className == "org.gradle.api.plugins.JavaPlugin" } == null
        }
        return result
    }

    def getPluginConfigurationOperationResult(String displayName) {
        (ProjectConfigurationOperationResult) events.operation("Configure project $displayName").result
    }

    private void runBuild(String task, Set<OperationType> operationTypes = EnumSet.of(OperationType.PROJECT_CONFIGURATION), Action<BuildLauncher> config = {}) {
        withConnection {
            def launcher = newBuild()
                .forTasks(task)
                .addProgressListener(events, operationTypes)
            collectOutputs(launcher)
            config.execute(launcher)
            launcher.run()
        }
    }

}
