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
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult

import java.time.Duration

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.1')
class ProjectConfigurationProgressEventCrossVersionSpec extends ToolingApiSpecification {

    ProgressEvents events = ProgressEvents.create()

    void setup() {
        file("buildSrc/settings.gradle") << """
            include 'a'
        """
        settingsFile << """
            rootProject.name = 'root'
            include 'b'
        """
        file("included/settings.gradle") << """
            include 'c'
        """
    }

    def "reports successful project configuration progress events"() {
        when:
        runBuild("tasks", ["--include-build", "included"], EnumSet.allOf(OperationType))

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
        runBuild("tasks", EnumSet.allOf(OperationType))

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
        runBuild("tasks", ["--include-build", "included"], EnumSet.allOf(OperationType))

        then:
        containsPluginConfigurationResultsForJavaPlugin(":buildSrc")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":buildSrc:a")
        containsPluginConfigurationResultsForJavaPlugin(":")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":b")
        containsPluginConfigurationResultsForJavaPlugin(":included")
        doesNotContainPluginConfigurationResultsForJavaPlugin(":included:c")
    }

    void containsPluginConfigurationResultsForJavaPlugin(String displayName) {
        with((ProjectConfigurationOperationResult) events.operation("Configure project $displayName").result) {
            def javaPluginResult = pluginConfigurationResults.find { it.plugin.className == "org.gradle.api.plugins.JavaPlugin" }
            assert javaPluginResult.plugin.pluginId == "org.gradle.java"
            assert javaPluginResult.duration >= Duration.ZERO
            def basePluginResult = pluginConfigurationResults.find { it.plugin.className == "org.gradle.api.plugins.BasePlugin" }
            assert basePluginResult.plugin.pluginId == null
            assert basePluginResult.duration >= Duration.ZERO
        }
    }

    void doesNotContainPluginConfigurationResultsForJavaPlugin(String displayName) {
        with((ProjectConfigurationOperationResult) events.operation("Configure project $displayName").result) {
            assert pluginConfigurationResults.find { it.plugin.className == "org.gradle.api.plugins.JavaPlugin" } == null
        }
    }

    private void runBuild(String task, List<String> arguments = [], Set<OperationType> operationTypes) {
        withConnection {
            newBuild()
                .forTasks(task)
                .withArguments(arguments)
                .addProgressListener(events, operationTypes)
                .run()
        }
    }

}
