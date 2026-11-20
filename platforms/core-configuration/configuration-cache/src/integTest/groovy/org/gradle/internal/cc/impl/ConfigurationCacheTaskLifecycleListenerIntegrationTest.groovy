/*
 * Copyright 2026 the original author or authors.
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

class ConfigurationCacheTaskLifecycleListenerIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "listener accessing Task.project in #method is reported as a problem"() {
        buildFile """
            abstract class ProjectAccessingListener implements TaskLifecycleListener<Task> {
                void $method {
                    println "listener sees project \${task.project.name}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(ProjectAccessingListener)
            }
        """

        when:
        configurationCacheFails "foo"

        then:
        // The access fails the task, so the problem is reported without failing the build a second time
        failure.assertHasCause("Invocation of 'Task.project' by task ':foo' at execution time is unsupported with the configuration cache.")
        problems.assertResultHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }

        where:
        method << ["beforeExecution(Task task)", "afterExecution(Task task, TaskState state)"]
    }

    def "listener on tasks building an included plugin is only notified when the cache entry is stored"() {
        def configurationCache = newConfigurationCacheFixture()
        pluginBuildWithListenerOnItsTasks()

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateStored()
        outputContains("plugin listener before :compileJava")
        outputContains("plugin listener before :jar")

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain("plugin listener before")
    }

    def "listener on requested tasks of an included plugin build is notified when the cache entry is reused"() {
        def configurationCache = newConfigurationCacheFixture()
        pluginBuildWithListenerOnItsTasks()

        when:
        configurationCacheRun "foo", ":plugin:hello"

        then:
        configurationCache.assertStateStored()
        outputContains("plugin listener before :compileJava")
        outputContains("plugin listener before :hello\naction of hello")

        when:
        configurationCacheRun "foo", ":plugin:hello"

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain("plugin listener before :compileJava")
        outputContains("plugin listener before :hello\naction of hello")
    }

    private void pluginBuildWithListenerOnItsTasks() {
        settingsFile("plugin/settings.gradle", """
            rootProject.name = "plugin"
        """)
        buildFile("plugin/build.gradle", """
            plugins {
                id("java-gradle-plugin")
            }

            gradlePlugin {
                plugins {
                    fooPlugin {
                        id = "foo"
                        implementationClass = "FooPlugin"
                    }
                }
            }

            abstract class PrintingListener implements TaskLifecycleListener<Task> {
                void beforeExecution(Task task) {
                    println "plugin listener before \${task.path}"
                }
            }

            tasks.addLifecycleListener(PrintingListener)

            tasks.register("hello") {
                doLast { println "action of hello" }
            }
        """)
        file("plugin/src/main/java/FooPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class FooPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("foo");
                }
            }
        """
        settingsFile """
            pluginManagement {
                includeBuild("plugin")
            }
        """
        buildFile """
            plugins {
                id("foo")
            }
        """
    }
}
