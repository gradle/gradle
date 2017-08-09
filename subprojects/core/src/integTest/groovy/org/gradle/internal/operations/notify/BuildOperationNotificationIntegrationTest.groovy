/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.notify

import groovy.json.JsonOutput
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType

class BuildOperationNotificationIntegrationTest extends AbstractIntegrationSpec {

    String registerListener() {
        """
            def listener = new $BuildOperationNotificationListener.name() {
                void started($BuildOperationStartedNotification.name notification) {
                    def details = notification.notificationOperationDetails
                    if (details instanceof $ExecuteTaskBuildOperationType.Details.name) {
                        details = [taskPath: details.taskPath, buildPath: details.buildPath, taskId: details.taskId, taskClass: details.taskClass.name]
                    } else  if (details instanceof $ApplyPluginBuildOperationType.Details.name) {
                        details = [pluginId: details.pluginId, pluginClass: details.pluginClass.name, targetType: details.targetType, targetPath: details.targetPath, buildPath: details.buildPath]
                    }
                    println "STARTED: \${notification.details.class.interfaces.first().name} - \${${JsonOutput.name}.toJson(details)} - \$notification.notificationOperationId - \$notification.notificationOperationParentId"   
                }

                void finished($BuildOperationFinishedNotification.name notification) {
                    println "FINISHED: \${notification.result?.class?.interfaces?.first()?.name} - \${${JsonOutput.name}.toJson(notification.notificationOperationResult)} - \$notification.notificationOperationId"
                }
            }
            def registrar = services.get($BuildOperationNotificationListenerRegistrar.name)
            registrar.registerBuildScopeListener(listener)
        """
    }

    def "emits notifications"() {
        when:
        buildScript """
           ${registerListener()}
            task t  
        """

        succeeds "t", "-S"

        then:
        started(CalculateTaskGraphBuildOperationType.Details, [:])
        finished(CalculateTaskGraphBuildOperationType.Result, [excludedTaskPaths: [], requestedTaskPaths: [":t"]])
    }

    def "emits notifications for nested builds"() {
        when:
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file("init.d/init.gradle") << """
            if (parent == null) {
                ${registerListener()}
            }
        """

        file("buildSrc/build.gradle") << ""
        file("a/buildSrc/build.gradle") << ""
        file("a/build.gradle") << "task t"
        file("a/settings.gradle") << ""
        file("settings.gradle") << "includeBuild 'a'"
        buildScript """
            task t {
                dependsOn gradle.includedBuild("a").task(":t")
            }
        """

        succeeds "t"

        then:
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":buildSrc", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a:buildSrc", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":a", projectPath: ":"])
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])
    }

    def "does not emit for GradleBuild tasks"() {
        when:
        def initScript = file("init.gradle") << """
            if (parent == null) {
                ${registerListener()}
            }
        """

        buildScript """
            task t(type: GradleBuild) {
                tasks = ["o"]
                startParameter.searchUpwards = false
            }
            task o
        """

        succeeds "t", "-I", initScript.absolutePath

        then:
        started(ConfigureProjectBuildOperationType.Details, [buildPath: ":", projectPath: ":"])

        // Rough test for not getting notifications for the nested build
        executedTasks.find { it.endsWith(":o") }
        output.count(ConfigureProjectBuildOperationType.Details.name) == 1
    }

    def "listeners are deregistered after build"() {
        when:
        executer.requireDaemon().requireIsolatedDaemons()
        buildFile << registerListener() << "task t"
        succeeds("t")

        then:
        finished(CalculateTaskGraphBuildOperationType.Result, [excludedTaskPaths: [], requestedTaskPaths: [":t"]])

        when:
        // remove listener
        buildFile.text = "task x"
        succeeds("x")

        then:
        output.count(CalculateTaskGraphBuildOperationType.Result.name) == 0
    }

    // This test simulates what the build scan plugin does.
    def "listener receives recorded operations happen before registration"() {
        given:
        file("buildSrc/build.gradle") << ""
        file("build.gradle") << """
            ${registerListener()}
            task t
        """

        when:
        succeeds "t"

        then:
        output.contains(":buildSrc:compileJava") // executedTasks check fails with in process executer
        output.count(ConfigureProjectBuildOperationType.Details.name) == 2
        output.count(ExecuteTaskBuildOperationType.Details.name) == 15 // including all buildSrc task execution events
    }

    void started(Class<?> type, Map<String, ?> payload) {
        has(true, type, payload)
    }

    void finished(Class<?> type, Map<String, ?> payload) {
        has(false, type, payload)
    }

    void has(boolean started, Class<?> type, Map<String, ?> payload) {
        def string = notificationLogLine(started, type, payload)
        assert output.contains(string)
    }

    String notificationLogLine(boolean started, Class<?> type, Map<String, ?> payload) {
        "${started ? "STARTED" : "FINISHED"}: $type.name - ${JsonOutput.toJson(payload)}"
    }
}
