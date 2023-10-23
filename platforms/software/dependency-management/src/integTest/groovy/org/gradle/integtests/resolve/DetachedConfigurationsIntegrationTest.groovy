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

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL

@FluidDependenciesResolveTest
class DetachedConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2889")
    def "detached configurations may have separate dependencies"() {
        given:
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
                    maven { url "${mavenRepo.uri}" }
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

    // This behavior will be removed in Gradle 9.0
    @Deprecated
    def "detached configurations can contain artifacts and resolve them during a self-dependency scenario"() {
        given:
        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            def detached = project.configurations.detachedConfiguration()
            detached.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            detached.dependencies.add(project.dependencies.create(project))

            task makeArtifact(type: Zip) {
                archiveFileName = "artifact.zip"
                from "artifact.txt"
            }

            detached.outgoing.artifact(tasks.makeArtifact)

            task checkDependencies {
                def result = detached.incoming.resolutionResult.rootComponent
                def artifacts = detached.incoming.artifacts

                doLast {
                    def depModuleNames = result.get().dependencies*.selected*.moduleVersion*.name
                    def artifactNames = artifacts.artifacts.collect { it.file.name }
                    assert depModuleNames.contains('test')
                    assert artifactNames.contains("artifact.zip")
                }
            }
        """

        file("artifact.txt") << "sample artifact"

        expect:
        executer.expectDocumentedDeprecationWarning("The detachedConfiguration1 configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        executer.expectDocumentedDeprecationWarning("While resolving configuration 'detachedConfiguration1', it was also selected as a variant. Configurations should not act as both a resolution root and a variant simultaneously. Depending on the resolved configuration in this manner has been deprecated. This will fail with an error in Gradle 9.0. Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#depending_on_root_configuration")

        run "checkDependencies"
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
        succeeds "help"

        where:
        name << ["detachedConfiguration", "detachedConfiguration1", "detachedConfiguration22902"]
    }
}
