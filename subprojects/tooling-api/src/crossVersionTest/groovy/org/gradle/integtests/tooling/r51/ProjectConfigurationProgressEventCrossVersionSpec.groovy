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

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult
import org.junit.Rule

import java.time.Duration

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.1')
class ProjectConfigurationProgressEventCrossVersionSpec extends ToolingApiSpecification {

    ProgressEvents events = ProgressEvents.create()

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

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
            assert projectConfiguration
            with((ProjectConfigurationOperationDescriptor) descriptor) {
                assert project.projectPath == projectPath
                assert project.buildIdentifier.rootDir == rootDir
            }
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
            projectConfiguration
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
        containsPluginConfigurationResultsForJavaPlugin(":buildSrc")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":buildSrc:a")
        containsPluginConfigurationResultsForJavaPlugin(":")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":b")
        containsPluginConfigurationResultsForJavaPlugin(":included")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":included:c")
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
        containsPluginConfigurationResultsForJavaPluginAndScriptPlugins(":buildSrc", file("buildSrc"))
        doesNotContainPluginConfigurationResultsForJavaPluginAndScriptPlugins(":buildSrc:a")
        containsPluginConfigurationResultsForJavaPluginAndScriptPlugins(":", projectDir)
        doesNotContainPluginConfigurationResultsForJavaPluginAndScriptPlugins(":b")
        containsPluginConfigurationResultsForJavaPluginAndScriptPlugins(":included", file("included"))
        doesNotContainPluginConfigurationResultsForJavaPluginAndScriptPlugins(":included:c")
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
        containsPluginConfigurationResultsForJavaPluginAndScriptPlugins(":b", file("b"))
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
        def plugins = getPluginConfigurationOperationResult(":").getPluginConfigurationResults().collect { it.plugin.displayName }
        plugins == [
            "org.gradle.build-init", "org.gradle.wrapper", "org.gradle.help-tasks",
            "build.gradle", "script.gradle",
            "org.gradle.java", "org.gradle.api.plugins.JavaBasePlugin", "org.gradle.api.plugins.BasePlugin",
            "org.gradle.language.base.plugins.LifecycleBasePlugin", "org.gradle.api.plugins.ReportingBasePlugin"
        ]
    }

    def "reports plugin configuration results for remote script plugins"() {
        given:
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
        def result = getPluginConfigurationOperationResult(":").getPluginConfigurationResults().find { it.plugin.displayName == "script.gradle" }
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
        getPluginConfigurationOperationResult(":").getPluginConfigurationResults().findAll { it.plugin.displayName.contains("MyPlugin") }.empty
    }

    void containsPluginConfigurationResultsForJavaPluginAndScriptPlugins(String displayName, File buildscriptDir) {
        with(containsPluginConfigurationResultsForJavaPlugin(displayName)) {
            def buildScript = pluginConfigurationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(buildscriptDir, "build.gradle").toURI() }
            assert buildScript.duration >= Duration.ZERO
            assert buildScript.plugin.displayName == "build.gradle"
            def scriptPlugin = pluginConfigurationResults.find { it.plugin instanceof ScriptPluginIdentifier && it.plugin.uri == new File(projectDir, "script.gradle").toURI() }
            assert scriptPlugin.plugin.displayName == "script.gradle"
            assert scriptPlugin.duration >= Duration.ZERO
        }
    }

    ProjectConfigurationOperationResult containsPluginConfigurationResultsForJavaPlugin(String displayName) {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            def javaPluginResult = pluginConfigurationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.JavaPlugin" }
            assert javaPluginResult.plugin.pluginId == "org.gradle.java"
            assert javaPluginResult.plugin.displayName == "org.gradle.java"
            assert javaPluginResult.duration >= Duration.ZERO
            def basePluginResult = pluginConfigurationResults.find { it.plugin instanceof BinaryPluginIdentifier && it.plugin.className == "org.gradle.api.plugins.BasePlugin" }
            assert basePluginResult.plugin.pluginId == null
            assert basePluginResult.plugin.displayName == "org.gradle.api.plugins.BasePlugin"
            assert basePluginResult.duration >= Duration.ZERO
        }
        return result
    }

    void doesNotContainPluginConfigurationResultsForJavaPluginAndScriptPlugins(String displayName) {
        with(doesNotContainPluginConfigurationResultsForJavaPlugin(displayName)) {
            assert pluginConfigurationResults.findAll { it.plugin instanceof ScriptPluginIdentifier }.empty
        }
    }

    ProjectConfigurationOperationResult doesNotContainPluginConfigurationResultsForJavaPlugin(String displayName) {
        def result = getPluginConfigurationOperationResult(displayName)
        with(result) {
            assert pluginConfigurationResults.find { it.plugin.className == "org.gradle.api.plugins.JavaPlugin" } == null
        }
        return result
    }

    def getPluginConfigurationOperationResult(String displayName) {
        (ProjectConfigurationOperationResult) events.operation("Configure project $displayName").result
    }

    private void runBuild(String task, Set<OperationType> operationTypes = EnumSet.of(OperationType.PROJECT_CONFIGURATION)) {
        withConnection {
            newBuild()
                .forTasks(task)
                .addProgressListener(events, operationTypes)
                .run()
        }
    }

}
