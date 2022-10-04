/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import spock.lang.IgnoreIf
import spock.lang.Issue

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger

class ConfigurationCacheBuildServiceIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue('https://github.com/gradle/gradle/issues/20001')
    def "build service from buildSrc is not restored"() {
        given:
        def onFinishMessage = "You won't see me!"
        withListenerBuildServicePlugin onFinishMessage
        createDir('buildSrc') {
            file('settings.gradle') << """
                pluginManagement {
                    repositories {
                        maven { url '$mavenRepo.uri' }
                    }
                }
            """
            file('build.gradle') << """
                plugins { id 'listener-build-service-plugin' version '1.0' }
            """
        }
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun()
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain onFinishMessage
    }

    def "build service is restored when using @ServiceReference"() {
        given:
        withCountingServicePlugin()
        file('settings.gradle') << """
            pluginManagement {
                repositories {
                    maven { url '$mavenRepo.uri' }
                }
            }
        """
        file('build.gradle') << """
            plugins { id 'counting-service-plugin' version '1.0' }

            tasks.register('count', CountingTask) {
                doLast {
                    assert countingService.get().increment() == 2
                }
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun(":count")

        then:
        configurationCache.assertStateStored()
        outputContains 'Count: 1'

        when:
        configurationCacheRun(":count")

        then:
        configurationCache.assertStateLoaded()
        outputContains 'Count: 1'
    }

    private void withCountingServicePlugin() {
        createDir('plugin') {
            file("src/main/java/CountingServicePlugin.java") << """
                public abstract class CountingServicePlugin implements $Plugin.name<$Project.name> {
                    private $Provider.name<?> counterProvider;
                    @Override
                    public void apply($Project.name project) {
                        project.getGradle().getSharedServices().registerIfAbsent(
                            "counter",
                            CountingService.class,
                            (spec) -> {}
                        );
                    }
                }

                abstract class CountingTask extends $DefaultTask.name {

                    @$ServiceReference.name("counter")
                    public abstract $Property.name<CountingService> getCountingService();

                    @$Inject.name
                    public CountingTask() {}

                    @$TaskAction.name
                    void doIt() {
                        System.out.println("Count: " + getCountingService().get().increment());
                    }
                }

                abstract class CountingService implements
                    $BuildService.name<${BuildServiceParameters.name}.None> {

                    private ${AtomicInteger.name} count = new ${AtomicInteger.name}();

                    @$Inject.name
                    public CountingService() {}

                    public int increment() {
                        return count.incrementAndGet();
                    }
               }
            """
            file("build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                    id("maven-publish")
                }
                group = "com.example"
                version = "1.0"
                publishing {
                    repositories {
                        maven { url '$mavenRepo.uri' }
                    }
                }
                gradlePlugin {
                    plugins {
                        buildServicePlugin {
                            id = 'counting-service-plugin'
                            implementationClass = 'CountingServicePlugin'
                        }
                    }
                }
            """
        }
        executer.inDirectory(file("plugin")).withTasks("publish").run()
    }

    private void withListenerBuildServicePlugin(String onFinishMessage) {
        createDir('plugin') {
            file("src/main/java/BuildServicePlugin.java") << """
                import org.gradle.api.Plugin;

                public abstract class BuildServicePlugin implements Plugin<$Project.name> {

                    @$Inject.name protected abstract $BuildEventsListenerRegistry.name getListenerRegistry();

                    @Override
                    public void apply($Project.name project) {
                        final String projectName = project.getName();
                        final String uniqueServiceName = "listener of " + project.getName();
                        getListenerRegistry().onTaskCompletion(
                            project.getGradle().getSharedServices().registerIfAbsent(
                                uniqueServiceName,
                                ListenerBuildService.class,
                                (spec) -> {}
                            )
                        );
                    }
                }

                abstract class ListenerBuildService implements
                    $BuildService.name<${BuildServiceParameters.name}.None>,
                    $OperationCompletionListener.name {

                    @$Inject.name
                    public ListenerBuildService() {}

                    @Override
                    public void onFinish($FinishEvent.name event) {
                        System.out.println("$onFinishMessage");
                    }
                }
            """
            file("build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                    id("maven-publish")
                }
                group = "com.example"
                version = "1.0"
                publishing {
                    repositories {
                        maven { url '$mavenRepo.uri' }
                    }
                }
                gradlePlugin {
                    plugins {
                        buildServicePlugin {
                            id = 'listener-build-service-plugin'
                            implementationClass = 'BuildServicePlugin'
                        }
                    }
                }
            """
        }
        executer.inDirectory(file("plugin")).withTasks("publish").run()
    }

    @IgnoreIf({ GradleContextualExecuter.isNoDaemon() })
    def "build service from included build is loaded in reused classloader"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        createDir("probe-plugin") {
            file("settings.gradle") << "rootProject.name = 'probe-plugin'"
            file("build.gradle") << """
                plugins {
                    id 'java-gradle-plugin'
                }
                version = '1.0'
                gradlePlugin {
                    plugins {
                        probePlugin {
                            id  = 'probe-plugin'
                            implementationClass = 'my.ProbePlugin'
                        }
                    }
                }
            """
            file("src/main/java/my/ProbePlugin.java") << """
                package my;

                import org.gradle.api.*;

                /**
                 * Holds a static variable so ClassLoader reuse can be probed.
                 */
                class Static {
                    private static final $AtomicInteger.name value = new $AtomicInteger.name(0);
                    public static String probe(String id) {
                        // When ClassLoaders are reused
                        // the 1st run should print `probe(id) => 1`
                        // the 2nd run should print `probe(id) => 2`
                        // and so on.
                        return "probe(" + id + ") => " + value.incrementAndGet();
                    }
                }

                interface ProbeServiceParameters extends $BuildServiceParameters.name {
                    $Property.name<String> getProjectName();
                }

                abstract class ProbeService implements
                    $BuildService.name<ProbeServiceParameters>,
                    $OperationCompletionListener.name {

                    @$Inject.name
                    public ProbeService() {}

                    public void probe(String eventName, String taskPath) {
                        String probe = Static.probe(getParameters().getProjectName().get());
                        System.out.println(eventName + ": " + taskPath + ": " + probe);
                    }

                    @Override
                    public void onFinish($FinishEvent.name event) {
                        if (event instanceof $TaskFinishEvent.name) {
                            probe(
                                "onFinish",
                                (($TaskFinishEvent.name) event).getDescriptor().getTaskPath()
                            );
                        }
                    }
                }

                abstract class ProbeTask extends DefaultTask {

                    @$Internal.name
                    public abstract $Property.name<ProbeService> getProbeService();

                    @$Inject.name
                    public ProbeTask() {}

                    @$TaskAction.name
                    void probe() {
                        getProbeService().get().probe("onTaskAction", getPath());
                    }
                }

                public abstract class ProbePlugin implements Plugin<Project> {

                    @$Inject.name protected abstract $BuildEventsListenerRegistry.name getListenerRegistry();

                    public void apply(Project project) {
                        final String projectName = project.getName();
                        final String uniqueServiceName = "listener of " + project.getName();
                        final $Provider.name<ProbeService> probeService = project.getGradle().getSharedServices().registerIfAbsent(
                            uniqueServiceName,
                            ProbeService.class,
                            (spec) -> spec.getParameters().getProjectName().set(projectName)
                        );
                        getListenerRegistry().onTaskCompletion(
                            probeService
                        );
                        project.getTasks().register("probe", ProbeTask.class, (task) -> {
                            task.getProbeService().set(probeService);
                        });
                    }
                }
            """
        }
        createDir("included") {
            file("settings.gradle") << """
                pluginManagement {
                    includeBuild '../probe-plugin'
                }
                include 'classloader1', 'boundary:classloader2'
            """
            file("classloader1/build.gradle") << """
                plugins { id 'probe-plugin' version '1.0' }
            """
            file("boundary/build.gradle.kts") << """
                plugins { `kotlin-dsl` } // put a boundary between classloader1 and classloader2
            """
            file("boundary/classloader2/build.gradle") << """
                plugins { id 'probe-plugin' version '1.0' }
            """
        }
        createDir("root") {
            file("settings.gradle") << """
                includeBuild '../included'
            """
        }

        and:
        // classloader reuse requires daemon reuse without memory pressure
        executer.requireIsolatedDaemons()

        when:
        inDirectory 'root'
        configurationCacheRun ':included:classloader1:probe', ':included:boundary:classloader2:probe'

        then: 'on classloader classloader1'
        outputContains 'probe(classloader1) => 1'
        outputContains 'probe(classloader1) => 2'
        outputContains 'probe(classloader1) => 3'

        and: 'on classloader classloader2'
        outputContains 'probe(classloader2) => 1'
        outputContains 'probe(classloader2) => 2'
        outputContains 'probe(classloader2) => 3'

        and:
        configurationCache.assertStateStored()

        when:
        inDirectory 'root'
        configurationCacheRun ':included:classloader1:probe', ':included:boundary:classloader2:probe'

        then:
        configurationCache.assertStateLoaded()

        and: 'classloader1 is reused'
        outputContains 'probe(classloader1) => 4'
        outputContains 'probe(classloader1) => 5'
        outputContains 'probe(classloader1) => 6'

        and: 'classloader2 is reused'
        outputContains 'probe(classloader2) => 4'
        outputContains 'probe(classloader2) => 5'
        outputContains 'probe(classloader2) => 6'
    }
}
