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

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger

class ConfigurationCacheCompositeBuildsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "loads build services from the correct build and reuses ClassLoaders"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        createDir("build-logic") {
            file("settings.gradle") << "rootProject.name = 'build-logic'"
            file("build.gradle") << """
                plugins {
                    id 'java-gradle-plugin'
                }
                version = '1.0'
                gradlePlugin {
                    plugins {
                        myConventionsPlugin {
                            id  = 'my.conventions'
                            implementationClass = 'my.ConventionsPlugin'
                        }
                    }
                }
            """
            file("src/main/java/my/ConventionsPlugin.java") << """
                package my;

                import org.gradle.api.*;

                /**
                 * Holds a static variable so ClassLoader reuse can be probed.
                 */
                class Static {
                    private static final $AtomicInteger.name value = new $AtomicInteger.name(0);
                    public static void probe(String id) {
                        // When ClassLoaders are reused
                        // the 1st run should print `probe(id) => 1`
                        // the 2nd run should print `probe(id) => 2`
                        // and so on.
                        System.out.println("probe(" + id + ") => " + value.incrementAndGet());
                    }
                }

                interface MyBuildServiceParameters extends $BuildServiceParameters.name {
                    $Property.name<String> getProjectName();
                }

                abstract class MyBuildService implements
                    $BuildService.name<MyBuildServiceParameters>,
                    $OperationCompletionListener.name {

                    @$Inject.name
                    public MyBuildService() {}

                    @Override
                    public void onFinish($FinishEvent.name event) {
                        if (event instanceof $TaskFinishEvent.name) {
                            System.out.println(
                                "TaskFinishEvent " + (($TaskFinishEvent.name) event).getDescriptor().getTaskPath()
                            );
                            Static.probe(getParameters().getProjectName().get());
                        }
                    }
                }

                public abstract class ConventionsPlugin implements Plugin<Project> {

                    @$Inject.name protected abstract $BuildEventsListenerRegistry.name getListenerRegistry();

                    public void apply(Project project) {
                        final String projectName = project.getName();
                        final String uniqueServiceName = "listener of " + project.getName();
                        getListenerRegistry().onTaskCompletion(
                            project.getGradle().getSharedServices().registerIfAbsent(
                                uniqueServiceName,
                                MyBuildService.class,
                                (spec) -> spec.getParameters().getProjectName().set(projectName)
                            )
                        );
                        project.getTasks().register("run", DefaultTask.class, (task) -> {
                            task.doLast((it) -> {
                                Static.probe(projectName);
                            });
                        });
                    }
                }
            """
        }
        createDir("included") {
            file("settings.gradle") << """
                include 'classloader1', 'boundary:classloader2'
            """
            file("classloader1/build.gradle") << """
                plugins { id 'my.conventions' version '1.0' }
            """
            file("boundary/build.gradle.kts") << """
                plugins { `kotlin-dsl` } // put a boundary between classloader1 and classloader2
            """
            file("boundary/classloader2/build.gradle") << """
                plugins { id 'my.conventions' version '1.0' }
            """
        }
        createDir("root") {
            file("settings.gradle") << """
                includeBuild '../build-logic'
                includeBuild '../included'
            """
        }

        when:
        inDirectory 'root'
        configurationCacheRun ':included:classloader1:run', ':included:boundary:classloader2:run'

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
        configurationCacheRun ':included:classloader1:run', ':included:boundary:classloader2:run'

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

    def "can use lib produced by included build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withAppBuild()
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib'
            """

            file('build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'
            """

            file('src/main/java/Lib.java') << """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }

        when:
        inDirectory 'app'
        configurationCacheRun 'run'

        then:
        outputContains 'Before!'
        configurationCache.assertStateStored()

        and: 'included build state is stored in a separate file with the correct permissions'
        def confCacheDir = file("./app/.gradle/configuration-cache")
        confCacheDir.isDirectory()
        def confCacheFiles = confCacheDir.allDescendants().findAll { it != 'configuration-cache.lock' && it != 'gc.properties' }
        confCacheFiles.size() == 3 // fingerprint, root build state file, included build state file
        if (!OperatingSystem.current().isWindows()) {
            confCacheFiles.forEach {
                assert confCacheDir.file(it).mode == 384
            }
        }

        when: 'changing source file from included build'
        file('lib/src/main/java/Lib.java').text = """
            public class Lib { public static void main() {
                System.out.println("After!");
            } }
        """

        and: 'rerunning the build'
        inDirectory 'app'
        configurationCacheRun 'run'

        then: 'it should pick up the changes'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
    }

    def "can use lib produced by multi-project included build with custom task"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withAppBuild()
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib-root'
                include 'lib'
            """

            file('lib/build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'

                class CustomTask extends DefaultTask {
                    @TaskAction def act() {
                        println 'custom task...'
                    }
                }

                def customTask = tasks.register('customTask', CustomTask)
                tasks.named('jar') {
                    dependsOn customTask
                }
            """

            file('lib/src/main/java/Lib.java') << """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }

        when:
        inDirectory 'app'
        configurationCacheRun 'run'

        then:
        outputContains 'custom task...'
        outputContains 'Before!'
        configurationCache.assertStateStored()

        when: 'changing source file from included build'
        file('lib/lib/src/main/java/Lib.java').text = """
            public class Lib { public static void main() {
                System.out.println("After!");
            } }
        """

        and: 'rerunning the build'
        inDirectory 'app'
        configurationCacheRun 'run'

        then: 'it should pick up the changes'
        outputContains 'custom task...'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
    }

    private TestFile withAppBuild() {
        createDir('app') {
            file('settings.gradle') << """
                includeBuild '../lib'
            """
            file('build.gradle') << """
                plugins {
                    id 'java'
                    id 'application'
                }
                application {
                   mainClass = 'Main'
                }
                dependencies {
                    implementation 'org.test:lib:1.0'
                }
            """
            file('src/main/java/Main.java') << """
                class Main { public static void main(String[] args) {
                    Lib.main();
                } }
            """
        }
    }

    def "reports a problem when source dependencies are present"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:buildB") {
                        from(GitVersionControlSpec) {
                            url = uri("some-repo")
                        }
                    }
                }
            }
        """

        and:
        def expectedProblem = "Gradle runtime: support for source dependencies is not yet implemented with the configuration cache."

        when:
        configurationCacheFails("help")

        then:
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient("help")

        then:
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheFails("help")

        then:
        configurationCache.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient("help")

        then:
        configurationCache.assertStateLoaded()
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }
    }
}
