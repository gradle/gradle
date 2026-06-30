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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

/**
 * Tests configuration-cache serialization of the provider returned by
 * {@code ArtifactCollection.getResolvedArtifacts()} when used as a task input
 * of type {@code SetProperty<ResolvedArtifactResult>}.
 * <p>
 * The historical defect (issue 27582) was asymmetric: configurations with no
 * task-producing dependencies hit the eager {@code fixedValue(get())} branch
 * of {@link org.gradle.api.internal.provider.BuildableBackedProvider} and
 * forced configuration-cache storage of {@code DefaultResolvedArtifactResult},
 * which had no codec. Configurations containing a project dependency took the
 * deferred {@code changingValue(...)} branch and avoided the failure.
 * <p>
 * Both variants now store and reload through the configuration cache. These
 * tests exist to ensure the regression cannot recur.
 */
@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
@Issue("https://github.com/gradle/gradle/issues/27582")
class ResolvedArtifactsWithConfigurationCacheIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def "resolvedArtifacts provider is serializable with only external dependencies"() {
        given:
        mavenRepo.module("commons-cli", "commons-cli", "1.6.0").publish()

        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation "commons-cli:commons-cli:1.6.0"
            }

            tasks.register("demo", DemoTask) {
                artifacts = configurations.runtimeClasspath.incoming.artifacts.resolvedArtifacts
            }

            abstract class DemoTask extends DefaultTask {
                @Internal
                abstract SetProperty<ResolvedArtifactResult> getArtifacts()

                @TaskAction
                void print() {
                    artifacts.get().each {
                        println it.file
                    }
                }
            }
        """

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateStored()
        outputContains("commons-cli-1.6.0.jar")

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateLoaded()
        outputContains("commons-cli-1.6.0.jar")
    }

    def "resolvedArtifacts provider is serializable for a bare resolvable configuration with no task dependencies"() {
        given:
        mavenRepo.module("commons-cli", "commons-cli", "1.6.0").publish()

        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
            plugins {
                id 'jvm-ecosystem'
            }

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                }
            }

            dependencies {
                deps "commons-cli:commons-cli:1.6.0"
            }

            tasks.register("demo", DemoTask) {
                artifacts = configurations.res.incoming.artifacts.resolvedArtifacts
            }

            abstract class DemoTask extends DefaultTask {
                @Internal
                abstract SetProperty<ResolvedArtifactResult> getArtifacts()

                @TaskAction
                void print() {
                    artifacts.get().each {
                        println it.file
                    }
                }
            }
        """

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateStored()
        outputContains("commons-cli-1.6.0.jar")

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateLoaded()
        outputContains("commons-cli-1.6.0.jar")
    }

    def "resolvedArtifacts provider is serializable with external and project dependencies"() {
        given:
        mavenRepo.module("commons-cli", "commons-cli", "1.6.0").publish()

        settingsFile << """
            rootProject.name = 'root'
            include 'some-other-project'
        """

        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation "commons-cli:commons-cli:1.6.0"
                implementation project(":some-other-project")
            }

            tasks.register("demo", DemoTask) {
                artifacts = configurations.runtimeClasspath.incoming.artifacts.resolvedArtifacts
            }

            abstract class DemoTask extends DefaultTask {
                @Internal
                abstract SetProperty<ResolvedArtifactResult> getArtifacts()

                @TaskAction
                void print() {
                    artifacts.get().each {
                        println it.file
                    }
                }
            }
        """

        file("some-other-project/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateStored()
        outputContains("commons-cli-1.6.0.jar")
        outputContains("build/libs/some-other-project.jar".replace('/', File.separator))

        when:
        succeeds "demo"

        then:
        configurationCache.assertStateLoaded()
        outputContains("commons-cli-1.6.0.jar")
    }
}
