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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.util.internal.ToBeImplemented
import spock.lang.IgnoreIf
import spock.lang.Issue

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger

class ConfigurationCacheBuildServiceIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "BuildOperationListener build service is instantiated only once per build"() {
        given:
        buildFile """
            abstract class ListenerService
                implements $BuildService.name<${BuildServiceParameters.name}.None>, $BuildOperationListener.name {

                public ListenerService() {
                    println('onInstantiated')
                }

                // Shouldn't be called
                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {
                    println('onStarted')
                }

                // Shouldn't be called
                void progress($OperationIdentifier.name operationIdentifier, $OperationProgressEvent.name progressEvent) {
                    println('onProgress')
                }

                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                    println('onFinished')
                }
            }

            def listener = gradle.sharedServices.registerIfAbsent("listener", ListenerService) { }
            def registry = services.get(BuildEventsListenerRegistry)
            registry.onOperationCompletion(listener)
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun()

        then: 'finish event is dispatched but start and progress are not'
        output.count('onInstantiated') == 1
        outputDoesNotContain 'onStarted'
        outputDoesNotContain 'onProgress'
        outputContains 'onFinished'

        when:
        configurationCacheRun()

        then: 'behaves the same'
        configurationCache.assertStateLoaded()
        output.count('onInstantiated') == 1
        outputDoesNotContain 'onStarted'
        outputDoesNotContain 'onProgress'
        outputContains 'onFinished'
    }

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

    def "build service is restored"(String serviceName, boolean finalize, boolean finalizeOnRead) {
        given:
        def legacy = serviceName == null
        def propertyAnnotations = legacy ?
            """@$Internal.name""" :
            """@$ServiceReference.name("$serviceName")"""

        withCountingServicePlugin(true, propertyAnnotations)
        file('settings.gradle') << """
            pluginManagement {
                includeBuild 'counting-service-plugin'
            }
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        file('build.gradle') << """
            plugins { id 'counting-service-plugin' version '1.0' }

            def altServiceProvider = project.getGradle().getSharedServices().registerIfAbsent(
                "counter",
                CountingService.class,
                (spec) -> {}
            );

            tasks.register('count', CountingTask) {
                ${ legacy ? """
                countingService.convention(altServiceProvider)
                usesService(altServiceProvider)
                """ : "" }
                ${ finalizeOnRead ? "countingService.finalizeValueOnRead()" : "" }
                ${ finalize ? "countingService.finalizeValue()" : "" }
                doLast {
                    assert countingService.get().increment() == 2
                    assert requiredServices.elements.size() == 1
                    assert altServiceProvider.get().increment() == 3
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

        where:
        serviceName | finalize | finalizeOnRead
        null        | false    | false
        null        | true     | false
        null        | false    | true
        "counter"   | false    | false
        "counter"   | true     | false
        "counter"   | false    | true
        ""          | false    | false
        ""          | true     | false
        ""          | false    | true
    }

    def "missing build service when using @ServiceReference"() {
        given:
        withCountingServicePlugin(true, """
            @$ServiceReference.name("consumedCounter")
            @$Optional.name
        """)
        file('settings.gradle') << """
            pluginManagement {
                includeBuild 'counting-service-plugin'
            }
        """
        file('build.gradle') << """
            plugins { id 'counting-service-plugin' version '1.0' }

            tasks.register('failedCount', CountingTask) {
                doLast {
                    assert countingService.getOrNull() == null
                }
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheFails(":failedCount")

        then:
        configurationCache.assertStateStored()
        failureDescriptionContains("Execution failed for task ':failedCount'.")
        failureCauseContains("Cannot query the value of task ':failedCount' property 'countingService' because it has no value available.")

        when:
        configurationCacheFails(":failedCount")

        then:
        configurationCache.assertStateLoaded()
        failureDescriptionContains("Execution failed for task ':failedCount'.")
        failureCauseContains("Cannot query the value of task ':failedCount' property 'countingService' because it has no value available.")
    }

    private void withCountingServicePlugin(boolean register, String propertyAnnotations) {
        createDir('counting-service-plugin') {
            file("src/main/java/CountingServicePlugin.java") << """
                public abstract class CountingServicePlugin implements $Plugin.name<$Project.name> {
                    private $Provider.name<?> counterProvider;
                    @Override
                    public void apply($Project.name project) {
                        ${register ? """
                        project.getGradle().getSharedServices().registerIfAbsent(
                            "counter",
                            CountingService.class,
                            (spec) -> {}
                        );
                        """ : ""}
                    }
                }

                abstract class CountingTask extends $DefaultTask.name {

                    $propertyAnnotations
                    public abstract $Property.name<CountingService> getCountingService();

                    public CountingTask() {}

                    @$TaskAction.name
                    void doIt() {
                        System.out.println("Count: " + getCountingService().get().increment());
                    }
                }

                abstract class CountingService implements
                    $BuildService.name<${BuildServiceParameters.name}.None> {

                    private ${AtomicInteger.name} count = new ${AtomicInteger.name}();

                    public CountingService() {}

                    public int increment() {
                        return count.incrementAndGet();
                    }
               }
            """
            file("build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                }
                group = "com.example"
                version = "1.0"
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

    @ToBeImplemented("https://github.com/gradle/gradle/issues/22337")
    def "build service can be used as input of value source obtained at configuration time"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            abstract class EmptyService implements BuildService<${BuildServiceParameters.name}.None> {
                int getValue() {
                    return 42
                }
            }

            abstract class ServiceValueSource implements ValueSource<Integer, Params> {
                interface Params extends ValueSourceParameters {
                    Property<EmptyService> getService()
                }
                @Override
                Integer obtain(){
                    return parameters.service.get().value
                }
            }
            def serviceProvider = gradle.sharedServices.registerIfAbsent("counter", EmptyService) {}
            def valueSource = providers.of(ServiceValueSource) {
                parameters {
                    service = serviceProvider
                }
            }.get()

            task check {
                doLast {
                    println "valueSource = " + valueSource
                }
            }
        """)

        when:
        configurationCacheRun "check"

        then:
        outputContains("valueSource = 42")
        // TODO(https://github.com/gradle/gradle/issues/22337) A clear error message should be provided at store time
        configurationCache.assertStateStored()
        expect:
        configurationCacheFails "check"
    }

    @Issue("https://github.com/gradle/gradle/issues/23700")
    def "build service registered as listener in an included build with no work is not restored"() {
        def onFinishMessage = "You won't see me!"
        withListenerBuildServicePlugin onFinishMessage

        def configurationCache = newConfigurationCacheFixture()
        createDir('included-build') {
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

        settingsScript("""
            includeBuild("included-build")
        """)

        buildScript("""
            tasks.register("check") {}
        """)

        when:
        configurationCacheRun "check"

        then:
        outputContains onFinishMessage

        when:
        configurationCacheRun "check"

        then:
        configurationCache.assertStateLoaded()
        outputDoesNotContain onFinishMessage
    }
}
