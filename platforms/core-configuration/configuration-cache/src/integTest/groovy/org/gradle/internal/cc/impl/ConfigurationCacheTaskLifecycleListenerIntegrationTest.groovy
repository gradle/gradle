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

    def "listener uses the same build service instance as other users of the service (completion listener: #completionListener)"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.api.services.ServiceReference
            import org.gradle.build.event.BuildEventsListenerRegistry
            import org.gradle.tooling.events.FinishEvent
            import org.gradle.tooling.events.OperationCompletionListener
            import javax.inject.Inject

            abstract class TrackingService implements BuildService<BuildServiceParameters.None>, OperationCompletionListener {
                TrackingService() {
                    println "service created \${System.identityHashCode(this)}"
                }

                void onFinish(FinishEvent event) {
                    println "completion listener uses service \${System.identityHashCode(this)}"
                }
            }

            abstract class TrackingListener implements TaskLifecycleListener<Task> {
                @ServiceReference("tracking")
                abstract Property<TrackingService> getService()

                void afterExecution(Task task, TaskState state) {
                    println "lifecycle listener of \${task.path} uses service \${System.identityHashCode(service.get())}"
                }
            }

            abstract class RegisteringPlugin implements Plugin<Project> {
                @Inject
                abstract BuildEventsListenerRegistry getEventsListenerRegistry()

                void apply(Project project) {
                    def service = project.gradle.sharedServices.registerIfAbsent("tracking", TrackingService) {}
                    if ($completionListener) {
                        eventsListenerRegistry.onTaskCompletion(service)
                    }
                    println "configuration uses service \${System.identityHashCode(service.get())}"
                }
            }

            pluginManager.apply(RegisteringPlugin)

            tasks.register("a") { addLifecycleListener(TrackingListener) }
            tasks.register("b") {
                dependsOn("a")
                addLifecycleListener(TrackingListener)
            }
        """

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateStored()
        def storeInstance = instances("lifecycle listener of ").first()
        instances("lifecycle listener of ") == [storeInstance, storeInstance]
        instances("completion listener uses service ") == (completionListener ? [storeInstance, storeInstance] : [])
        // A service registered as a completion listener is kept alive when the configuration cache is stored, other services are recreated
        !completionListener || instances("configuration uses service ") == [storeInstance]

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateLoaded()
        instances("configuration uses service ").isEmpty()
        def loadInstance = instances("lifecycle listener of ").first()
        instances("lifecycle listener of ") == [loadInstance, loadInstance]
        instances("completion listener uses service ") == (completionListener ? [loadInstance, loadInstance] : [])

        where:
        completionListener << [false, true]
    }

    def "listener from buildSrc is restored from the configuration cache"() {
        def configurationCache = newConfigurationCacheFixture()
        file("buildSrc/src/main/java/PrintingListener.java") << """
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskLifecycleListener;
            import org.gradle.api.tasks.TaskState;

            public abstract class PrintingListener implements TaskLifecycleListener<Task> {
                @Override
                public void beforeExecution(Task task) {
                    System.out.println("before " + task.getPath());
                }

                @Override
                public void afterExecution(Task task, TaskState state) {
                    System.out.println("after " + task.getPath());
                }
            }
        """
        buildFile """
            tasks.register("foo") {
                addLifecycleListener(PrintingListener)
                doLast { println "action of foo" }
            }
        """

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateStored()
        outputContains("before :foo\naction of foo\nafter :foo")

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateLoaded()
        outputContains("before :foo\naction of foo\nafter :foo")
    }

    def "listener and its build service from an included plugin build are restored from the configuration cache"() {
        def configurationCache = newConfigurationCacheFixture()
        file("plugin/settings.gradle") << "rootProject.name = 'plugin'"
        file("plugin/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    listenerPlugin {
                        id = "printing-listener"
                        implementationClass = "PrintingListenerPlugin"
                    }
                }
            }
        """
        file("plugin/src/main/java/PrintingListenerPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class PrintingListenerPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getGradle().getSharedServices().registerIfAbsent("printer", PrinterService.class, spec -> {});
                    project.getTasks().configureEach(task -> task.addLifecycleListener(PrintingListener.class));
                }
            }
        """
        file("plugin/src/main/java/PrinterService.java") << """
            import org.gradle.api.services.BuildService;
            import org.gradle.api.services.BuildServiceParameters;

            public abstract class PrinterService implements BuildService<BuildServiceParameters.None> {
                public void print(String message) {
                    System.out.println(message);
                }
            }
        """
        file("plugin/src/main/java/PrintingListener.java") << """
            import org.gradle.api.Task;
            import org.gradle.api.provider.Property;
            import org.gradle.api.services.ServiceReference;
            import org.gradle.api.tasks.TaskLifecycleListener;
            import org.gradle.api.tasks.TaskState;

            public abstract class PrintingListener implements TaskLifecycleListener<Task> {
                @ServiceReference("printer")
                public abstract Property<PrinterService> getPrinter();

                @Override
                public void beforeExecution(Task task) {
                    getPrinter().get().print("before " + task.getPath());
                }

                @Override
                public void afterExecution(Task task, TaskState state) {
                    getPrinter().get().print("after " + task.getPath());
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
                id("printing-listener")
            }

            tasks.register("foo") {
                doLast { println "action of foo" }
            }
        """

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateStored()
        outputContains("before :foo\naction of foo\nafter :foo")

        when:
        configurationCacheRun "foo"

        then:
        configurationCache.assertStateLoaded()
        outputContains("before :foo\naction of foo\nafter :foo")
    }

    private List<String> instances(String prefix) {
        output.readLines().findAll { it.startsWith(prefix) }.collect { it.tokenize(" ").last() }
    }
}
