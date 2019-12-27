/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.resolve.transform.ArtifactTransformTestFixture

class InstantExecutionDependencyResolutionIntegrationTest extends AbstractInstantExecutionIntegrationTest implements ArtifactTransformTestFixture {
    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def "task can have a field with type ArtifactCollection"() {
        taskTypeWithOutputFileProperty()
        mavenRepo.module("group", "lib1", "6500").publish()
        settingsFile << """
            include 'a', 'b'"""
        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    output = layout.buildDirectory.file("\${project.name}.out")
                }
                configurations.default.outgoing.artifact(producer.output)
            }
            repositories {
                maven { url = uri('${mavenRepo.uri}') }
            }
            configurations {
                implementation
            }
            dependencies {
                implementation project(':a')
                implementation project(':b')
                implementation "group:lib1:6500"
            }
            task resolve {
                def collection = configurations.implementation.incoming.artifacts
                inputs.files(collection.artifactFiles)
                doLast {
                    println("files = \${collection.artifactFiles.files.name}")
                    println("artifacts = \${collection.artifacts.id.displayName}")
                }
            }
        """

        expect:
        instantRun(":resolve")
        instantRun(":resolve")
        outputContains("files = [a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
    }

    def "task input file collection can include the output of artifact transform of project dependencies"() {
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                green {
                    attributes.attribute(color, 'green')
                }
            }
            dependencies {
                implementation project(':a')
                implementation files('root.green')
                implementation project(':b')
            }
        """
        file('root.green') << 'root'

        expect:
        instantRun(":resolve")
        assertTransformed("a.jar", "b.jar")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")

        instantRun(":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        assertTransformed("a.jar", "b.jar")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")
    }

    def "task input file collection can include the output of artifact transform of external dependencies"() {
        withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()

        setupBuildWithSimpleColorTransform()
        buildFile << """
            repositories {
                maven {
                    url = uri('${mavenRepo.uri}')
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                implementation "group:thing1:1.2"
                implementation "group:thing2:1.2"
            }
        """

        expect:
        instantRun(":resolve")
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")

        instantRun(":resolve")
        assertTransformed()
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input file collection can include the output of artifact transforms of prebuilt file dependencies"() {
        settingsFile << """
            include 'a'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files('root.blue')
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation files('a.blue')
                }
            }
        """
        file('root.blue') << 'root'
        file('a/a.blue') << 'a'

        expect:
        instantRun(":resolve")
        assertTransformed("root.blue", "a.blue", "a.jar")
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")

        instantRun(":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        assertTransformed("a.jar")
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")
    }

    def "task input file collection can include the output of chained artifact transform of project dependencies"() {
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithChainedColorTransform()
        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """

        expect:
        instantRun(":resolve")
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green]")

        instantRun(":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green")
    }

    def "task input file collection can include the output of artifact transform of project dependencies which takes the output of another transform as input parameter"() {
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransformWithAnotherTransformOutputAsInput()
        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
                transform project(':a')
            }
        """

        expect:
        instantRun(":resolve")
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")

        instantRun(":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")
    }

    def "task input file collection can include output of artifact transform of project dependencies which takes upstream artifacts"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        buildFile << """
            dependencies {
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
        """

        expect:
        instantRun(":resolve")
        output.count("processing") == 3
        outputContains("processing c.jar using []")
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        instantRun(":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        output.count("processing") == 3
        outputContains("processing c.jar using []")
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")
    }
}
