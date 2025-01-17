/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL

@FluidDependenciesResolveTest
class DetachedConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    @Issue("GRADLE-2889")
    def "detached configurations may have separate dependencies"() {
        given:
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        buildFile << """
            abstract class CheckDependencies extends DefaultTask {
                @Internal
                abstract Property<ResolvedComponentResult> getResult()

                @Internal
                abstract SetProperty<String> getDeclared()

                @TaskAction
                void test() {
                    def resolved = result.get().dependencies
                    assert declared.get() == resolved*.selected*.moduleVersion*.name as Set
                }
            }

            allprojects {
                configurations {
                    foo
                }
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }

                tasks.register("checkDependencies", CheckDependencies) {
                    def detached = project.configurations.detachedConfiguration(project.configurations.foo.dependencies as Dependency[])
                    result = detached.incoming.resolutionResult.rootComponent
                    declared = provider { project.configurations.foo.dependencies*.name }
                }
            }
            project(":a") {
                dependencies {
                    foo "org:foo:1.0"
                }
            }
            project(":b") {
                dependencies {
                    foo "org:bar:1.0"
                }
            }
        """

        expect:
        run "checkDependencies", "-S"
    }

    def "detached configurations may have dependencies on other projects"() {
        given:
        createDirs("other")
        settingsFile << "include 'other'"
        buildFile << """
            plugins {
                id 'java-library'
            }

            abstract class CheckDependencies extends DefaultTask {
                @Internal
                abstract Property<ResolvedComponentResult> getResult()

                @Internal
                ArtifactCollection artifacts

                @TaskAction
                void test() {
                    def depModuleNames = result.get().dependencies*.selected*.moduleVersion*.name
                    def artifactNames = artifacts.artifacts.collect { it.file.name }

                    assert depModuleNames.contains('other')
                    assert artifactNames.contains("other.jar")
                }
            }

            def detached = project.configurations.detachedConfiguration()
            detached.dependencies.add(project.dependencies.create(project(':other')))

            task checkDependencies(type: CheckDependencies) {
                result = detached.incoming.resolutionResult.rootComponent
                artifacts = detached.incoming.artifacts
            }

        """

        file("other/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        expect:
        run "checkDependencies"
    }

    @ToBeImplemented("Reverted to the old behavior to fix performance regression")
    @Issue("https://github.com/gradle/gradle/issues/30239")
    def "detached configuration can resolve project dependency targeting current project"() {
        buildFile << """
            task zip(type: Zip) {
                destinationDirectory = layout.buildDirectory.dir('dist')
                archiveBaseName = "foo"
            }

            configurations {
                consumable("foo") {
                    attributes {
                        attribute(Attribute.of("attr", String), "value")
                    }
                    outgoing.artifact(tasks.zip)
                }
            }

            def detached = configurations.detachedConfiguration()
            detached.attributes.attribute(Attribute.of("attr", String), "value")
            detached.dependencies.add(dependencies.create(project(":")))

            task resolve {
                def files = detached
                doLast {
                    assert files.files*.name == ["foo.zip"]
                }
            }
        """
        // Remove when test fixed:
        disableProblemsApiCheck()
        executer.noDeprecationChecks()

        expect:
        fails("resolve")
    }

    def "configurations container reserves name #name for detached configurations"() {
        given:
        buildFile << """
            configurations {
                $name
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Creating a configuration with a name that starts with 'detachedConfiguration' has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. Use a different name for the configuration '$name'. " +
            "Consult the upgrading guide for further information: ${BASE_URL}/userguide/upgrading_version_8.html#reserved_configuration_names")

        when:
        succeeds "help"

        then:
        verifyAll(receivedProblem(0)) {
            fqid == 'deprecation:creating-a-configuration-with-a-name-that-starts-with-detachedconfiguration'
            contextualLabel == 'Creating a configuration with a name that starts with \'detachedConfiguration\' has been deprecated.'
            solutions == ["Use a different name for the configuration '$name'.".toString()]
        }

        where:
        name << ["detachedConfiguration", "detachedConfiguration1", "detachedConfiguration22902"]
    }

    @ToBeImplemented("Reverted to the old behavior to fix performance regression")
    @Issue("https://github.com/gradle/gradle/issues/30239")
    def "detached configuration has a different component ID and module version ID than the root component"() {
        mavenRepo.module("org", "foo").publish()

        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("foo") {
                    extendsFrom(deps)
                }
            }

            if (${withDependencies}) {
                ${mavenTestRepository()}
                dependencies {
                    deps "org:foo:1.0"
                }
            }

            task resolve {
                def fooRoot = configurations.foo.incoming.resolutionResult.rootComponent

                def detached = configurations.detachedConfiguration(configurations.deps.dependencies as Dependency[])
                def detachedRoot = detached.incoming.resolutionResult.rootComponent

                if (${withDependencies}) {
                    assert configurations.foo.allDependencies.size() == 1
                    assert detached.allDependencies.size() == 1
                }

                doLast {
                    // We don't really care _what_ the detached configuration's IDs are.
                    // These really should be an implementation detail, as they are a synthetic ID and just need
                    // to be different than the project that owns the detached component.
                    assert fooRoot.get().id != detachedRoot.get().id
                    assert fooRoot.get().moduleVersion != detachedRoot.get().moduleVersion
                }
            }
        """
        // Remove when test fixed:
        disableProblemsApiCheck()
        executer.noDeprecationChecks()

        expect:
        fails("resolve")

        where:
        // We test with and without dependencies, to test with and without the
        // ShortCircuitEmptyConfigurationResolver.
        withDependencies << [true, false]
    }

    @ToBeImplemented("Reverted to the old behavior to fix performance regression")
    @Issue("https://github.com/gradle/gradle/issues/30239")
    def "can copy a detached configuration"() {
        mavenRepo.module("org", "foo").publish()

        buildFile << """
            task zip(type: Zip) {
                destinationDirectory = layout.buildDirectory.dir('dist')
                archiveBaseName = "test"
            }

            configurations {
                consumable("default") {
                    outgoing.artifact(tasks.zip)
                }
            }

            ${mavenTestRepository()}

            def copy = configurations.detachedConfiguration(
                dependencies.create(project(":")),
                dependencies.create("org:foo:1.0")
            ).copy()

            task resolve {
                def files = copy.incoming.files
                doLast {
                    assert files*.name  == ["test.zip", "foo-1.0.jar"]
                }
            }
        """
        // Remove when test fixed:
        disableProblemsApiCheck()
        executer.noDeprecationChecks()

        expect:
        fails("resolve")
    }

    @Issue("https://github.com/gradle/gradle/issues/30239")
    def "detached configuration can not extend configurations"() {
        disableProblemsApiCheck()

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            def detached = project.configurations.detachedConfiguration()
            detached.extendsFrom(project.configurations.implementation)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Calling extendsFrom on configuration ':detachedConfiguration1' has been deprecated. This will fail with an error in Gradle 9.0. Detached configurations should not extend other configurations, this was extending: 'implementation'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#detached_configurations_cannot_extend")
        succeeds "tasks"
    }
}
