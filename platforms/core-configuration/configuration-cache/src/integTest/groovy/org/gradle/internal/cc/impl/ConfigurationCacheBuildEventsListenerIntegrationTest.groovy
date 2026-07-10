/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.services.BuildServiceParameters
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent

class ConfigurationCacheBuildEventsListenerIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def configurationCache = new ConfigurationCacheFixture(this)

    def "can use build service provider as build events listener"() {
        given:
        buildFile """
            ${taskListenerService()}

            ${
                withBuildEventsListenerRegistryPlugin("""
                    def serviceProvider = gradle.sharedServices.registerIfAbsent("taskListener", TaskListenerService)
                    listenerRegistry.onTaskCompletion(serviceProvider)
                """)
            }

            tasks.register("run") { doLast {} }
        """

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateStored()
        outputContains("Completed: :run")

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Completed: :run")
    }

    def "cannot use mapped build service provider as build events listener"() {
        given:
        buildFile """
            ${taskListenerService()}

            ${
                withBuildEventsListenerRegistryPlugin("""
                    def serviceProvider = gradle.sharedServices.registerIfAbsent("taskListener", TaskListenerService)
                     listenerRegistry.onTaskCompletion(serviceProvider.map { it })
                """)
            }

            tasks.register("run") { doLast {} }
        """

        when:
        configurationCacheFails("run")

        then:
        assertUnsupportedListenerReported()
    }

    def "cannot use custom listener objects as build events listener"() {
        buildFile """
            ${nonServiceTaskListener()}
            ${
                withBuildEventsListenerRegistryPlugin("""
                    def listener = new TaskListener()
                    def listenerProvider = provider { listener }
                    listenerRegistry.onTaskCompletion(listenerProvider)
                """)
            }

            tasks.register("run") { doLast {} }
        """

        when:
        configurationCacheFails("run")

        then:
        assertUnsupportedListenerReported()
    }

    def "cannot use #object as build events listener in builds without work"() {
        buildFile("included/build.gradle", """
            ${nonServiceTaskListener()}
            ${taskListenerService()}

            ${
                withBuildEventsListenerRegistryPlugin("""
                    def listener = new TaskListener()
                    def listenerProvider = provider { listener }
                    def mappedServiceProvider = gradle.sharedServices.registerIfAbsent("taskListener", TaskListenerService).map { it }
                    listenerRegistry.onTaskCompletion($variable)
                """)
            }

            tasks.register("included") { doLast {} }
        """)

        settingsFile """
            includeBuild("included")
        """

        buildFile """ tasks.register("run") { doLast {} } """

        when:
        configurationCacheFails("run")

        then:
        assertUnsupportedListenerReported()

        where:
        object                          | variable
        "custom listener object"        | "listenerProvider"
        "mapped build service provider" | "mappedServiceProvider"
    }

    def "can ignore unsupported build events listener"() {
        buildFile """
            ${nonServiceTaskListener()}

            ${
            withBuildEventsListenerRegistryPlugin("""
                    def listener = new TaskListener()
                    def listenerProvider = provider { listener }
                    listenerRegistry.onTaskCompletion(listenerProvider)
                """)
            }

            tasks.register("run") { doLast {} }
        """

        when:
        configurationCacheRun("run", "-D${StartParameterBuildOptions.ConfigurationCacheIgnoreUnsupportedBuildEventsListeners.PROPERTY_NAME}=true")

        then:
        configurationCache.assertStateStored()
        // TODO(mlopatkin) Should we purge the non-service listeners to make cache-hit build close to load-after-store build?
        outputContains("Completed: :run")

        when:
        configurationCacheRun("run", "-D${StartParameterBuildOptions.ConfigurationCacheIgnoreUnsupportedBuildEventsListeners.PROPERTY_NAME}=true")

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain("Completed: :run")
    }

    def taskListenerService() {
        buildScriptSnippet """
            abstract class TaskListenerService implements BuildService<${BuildServiceParameters.name}.None>, ${OperationCompletionListener.name} {
                @Override
                void onFinish(${FinishEvent.name} event){
                    println("Completed: " + (event as ${TaskFinishEvent.name}).descriptor.taskPath)
                }
            }
        """
    }

    def nonServiceTaskListener() {
        buildScriptSnippet """
            class TaskListener implements ${OperationCompletionListener.name} {
                @Override
                void onFinish(${FinishEvent.name} event){
                    println("Completed: " + (event as ${TaskFinishEvent.name}).descriptor.taskPath)
                }
            }
        """
    }

    def withBuildEventsListenerRegistryPlugin(@GroovyBuildScriptLanguage impl) {
        """
            abstract class MyPlugin implements Plugin<Project> {
                @Inject
                abstract BuildEventsListenerRegistry getListenerRegistry()

                @Override
                void apply(Project project) {
                    project.tap {
                        $impl
                    }
                }
            }

            pluginManager.apply(MyPlugin)
        """
    }

    private void assertUnsupportedListenerReported(String location = "Plugin class 'MyPlugin'") {
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 1
            problemsWithStackTraceCount = 0
            withProblem("$location: Unsupported provider is registered as a task completion listener in 'org.gradle.build.event.BuildEventsListenerRegistry'. " +
                "Configuration Cache only supports providers returned from 'org.gradle.api.services.BuildServiceRegistry' as task completion listeners.")
        }
    }
}
