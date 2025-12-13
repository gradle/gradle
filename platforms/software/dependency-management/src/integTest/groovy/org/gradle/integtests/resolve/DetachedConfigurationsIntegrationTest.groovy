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
import spock.lang.Issue

@FluidDependenciesResolveTest
class DetachedConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    @Issue("GRADLE-2889")
    def "detached configurations may have separate dependencies"() {
        given:
        settingsFile << "include 'a', 'b'"
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()

        def common = """
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
        """

        buildFile << common
        file("a/build.gradle") << """
            $common

            dependencies {
                foo "org:foo:1.0"
            }
        """

        file("b/build.gradle") << """
            $common

            dependencies {
                foo "org:bar:1.0"
            }
        """

        expect:
        run "checkDependencies", "-S"
    }

    def "detached configurations may have dependencies on other projects"() {
        given:
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

        expect:
        succeeds("resolve")
    }

    def "configurations container reserves name #name for detached configurations"() {
        given:
        buildFile << """
            configurations {
                $name
            }
        """

        when:
        fails "help"

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        failure.assertHasCause("""Configuration name not allowed
  Creating a configuration with a name that starts with 'detachedConfiguration' is not allowed.  Use a different name for the configuration '$name'""")

        then:
        verifyAll(receivedProblem(0)) {
            fqid == 'configuration-usage:name-not-allowed'
            contextualLabel == "Creating a configuration with a name that starts with 'detachedConfiguration' is not allowed.  Use a different name for the configuration '$name'"
        }

        where:
        name << ["detachedConfiguration", "detachedConfiguration1", "detachedConfiguration22902"]
    }

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

        expect:
        succeeds("resolve")

        where:
        // We test with and without dependencies, to test with and without the
        // ShortCircuitEmptyConfigurationResolver.
        withDependencies << [true, false]
    }

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

        expect:
        succeeds("resolve")
    }

    @Issue("https://github.com/gradle/gradle/issues/30239")
    def "detached configuration can not extend #description"() {
        disableProblemsApiCheck()

        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            def detached = project.configurations.detachedConfiguration()
            detached.extendsFrom($extendsFromCall)
        """

        when:
        fails "tasks"

        then:
        failure.assertHasDescription("A problem occurred evaluating root project '${buildFile.parentFile.name}'.")
        failure.assertHasCause("""Extending a detachedConfiguration is not allowed
  configuration ':detachedConfiguration1' cannot extend $description""")

        where:
        extendsFromCall                                                                     | description
        "project.configurations.implementation"                                             | "configuration ':implementation'"
        "project.configurations.detachedConfiguration()"                                    | "configuration ':detachedConfiguration2'"
        "project.configurations.compileClasspath, project.configurations.runtimeClasspath"  | "configuration ':compileClasspath', configuration ':runtimeClasspath'"
    }
}
