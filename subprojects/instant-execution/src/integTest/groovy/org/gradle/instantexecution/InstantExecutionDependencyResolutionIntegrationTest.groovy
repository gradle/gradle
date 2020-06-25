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
import spock.lang.Ignore
import spock.lang.Issue

class InstantExecutionDependencyResolutionIntegrationTest extends AbstractInstantExecutionIntegrationTest implements ArtifactTransformTestFixture {
    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def setupBuildWithEachDependencyType() {
        taskTypeWithOutputFileProperty()

        mavenRepo.module("group", "lib1", "6500").publish()

        settingsFile << """
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.gradleProperty("\${project.name}Content").orElse("content")
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
            task additionalFile(type: FileProducer) {
                output = file("b.thing")
            }
            dependencies {
                implementation project(':a')
                implementation project(':b')
                implementation "group:lib1:6500"
                implementation files('a.thing', additionalFile.output)
            }
        """
    }

    def "task input file collection can include project dependencies, external dependencies, prebuilt file dependencies and task output file dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithEachDependencyType()
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            task resolve(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """

        given:
        instantRun(":resolve")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
    }

    def "task input artifact collection can include project dependencies, external dependencies, prebuilt file dependencies and task output file dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithEachDependencyType()
        taskTypeLogsArtifactCollectionDetails()

        buildFile << """
            task resolve(type: ShowArtifactCollection) {
                collection = configurations.implementation.incoming.artifacts
            }
        """

        given:
        instantRun(":resolve")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing, b.thing, a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
        outputContains("variants = [{artifactType=thing}, {artifactType=thing}, {artifactType=out}, {artifactType=out}, {artifactType=jar, org.gradle.status=release}]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing, b.thing, a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
        outputContains("variants = [{artifactType=thing}, {artifactType=thing}, {artifactType=out}, {artifactType=out}, {artifactType=jar, org.gradle.status=release}]")
    }

    def "task input property can include mapped configuration elements that contain project dependencies"() {
        def fixture = newInstantExecutionFixture()

        taskTypeWithOutputFileProperty()
        taskTypeWithInputListProperty()

        settingsFile << """
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.gradleProperty("\${project.name}Content").orElse("0")
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
            }
            task resolve(type: InputTask) {
                inValue = configurations.implementation.elements.map { files -> files.collect { it.asFile.text.toInteger() } }
                outFile = file('out.txt')
            }
        """

        given:
        instantRun(":resolve")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":resolve")
        file('out.txt').text == "10,10"

        when:
        instantRun(":resolve", "-PaContent=2")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskNotSkipped(":resolve")
        file('out.txt').text == "12,10"
    }

    def setupBuildWithArtifactTransformOfProjectDependencies() {
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
    }

    def "task input file collection can include the output of artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformOfProjectDependencies()

        when:
        instantRun(":resolve")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [root.green, a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        assertTransformed("a.jar")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")
    }

    def "task input artifact collection can include the output of artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformOfProjectDependencies()

        when:
        instantRun(":resolveArtifacts")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=green, color=green}, {artifactType=jar, color=green}, {artifactType=jar, color=green}]")

        when:
        instantRun(":resolveArtifacts")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=green, color=green}, {artifactType=jar, color=green}, {artifactType=jar, color=green}]")

        when:
        instantRun(":resolveArtifacts", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar")
        outputContains("files = [root.green, a.jar.green, b.jar.green]")
        outputContains("artifacts = [root.green, a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=green, color=green}, {artifactType=jar, color=green}, {artifactType=jar, color=green}]")
    }

    def setupBuildWithArtifactTransformsOfExternalDependencies() {
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
    }

    def "task input file collection can include the output of artifact transform of external dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfExternalDependencies()

        when:
        instantRun(":resolve")

        then:
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")

        when:
        instantRun(":resolve")

        then:
        fixture.assertStateLoaded()
        assertTransformed()
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input artifact collection can include the output of artifact transform of external dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfExternalDependencies()

        when:
        instantRun(":resolveArtifacts")

        then:
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("files = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")

        when:
        instantRun(":resolveArtifacts")

        then:
        fixture.assertStateLoaded()
        assertTransformed()
        outputContains("files = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")
    }

    def setupBuildWithArtifactTransformsOfPrebuiltFileDependencies() {
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
    }

    def "task input file collection can include the output of artifact transforms of prebuilt file dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfPrebuiltFileDependencies()

        when:
        instantRun(":resolve")

        then:
        assertTransformed("root.blue", "a.blue", "a.jar")
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        assertTransformed()
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")
    }

    def "task input artifact collection can include the output of artifact transforms of prebuilt file dependencies"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfPrebuiltFileDependencies()

        when:
        instantRun(":resolveArtifacts")

        then:
        assertTransformed("root.blue", "a.blue", "a.jar")
        outputContains("files = [root.blue.green, a.jar.green, a.blue.green]")
        outputContains("artifacts = [root.blue.green (root.blue), a.jar.green (project :a), a.blue.green (a.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")

        when:
        instantRun(":resolveArtifacts")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        assertTransformed()
        outputContains("files = [root.blue.green, a.jar.green, a.blue.green]")
        outputContains("artifacts = [root.blue.green (root.blue), a.jar.green (project :a), a.blue.green (a.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")
    }

    def setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            allprojects {
                task additionalFile(type: FileProducer) {
                    output = layout.buildDirectory.file("\${project.name}.additional.blue")
                }
            }
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files(tasks.additionalFile.output, 'root.blue')
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation files(tasks.additionalFile.output)
                }
            }
        """
        file('root.blue') << 'root'
    }

    @Issue("https://github.com/gradle/gradle/issues/13200")
    def "task input file collection can include the output of artifact transforms of file dependencies that include task outputs"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs()

        when:
        instantRun(":resolve")

        then:
        assertTransformed("root.blue", "root.additional.blue", "a.additional.blue", "a.jar")
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":a:additionalFile", ":resolve")
        result.assertTaskOrder(":additionalFile", ":resolve")
        assertTransformed()
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13200")
    def "task input artifact collection can include the output of artifact transforms of file dependencies that include task outputs"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs()

        when:
        instantRun(":resolveArtifacts")

        then:
        assertTransformed("root.blue", "root.additional.blue", "a.additional.blue", "a.jar")
        outputContains("files = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
        outputContains("artifacts = [root.additional.blue.green (root.additional.blue), root.blue.green (root.blue), a.jar.green (project :a), a.additional.blue.green (a.additional.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")

        when:
        instantRun(":resolveArtifacts")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":a:additionalFile", ":resolveArtifacts")
        result.assertTaskOrder(":additionalFile", ":resolveArtifacts")
        assertTransformed()
        outputContains("files = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
        outputContains("artifacts = [root.additional.blue.green (root.additional.blue), root.blue.green (root.blue), a.jar.green (project :a), a.additional.blue.green (a.additional.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")
    }

    def "task input file collection can include the output of chained artifact transform of project dependencies"() {
        def fixture = newInstantExecutionFixture()

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

        when:
        instantRun(":resolve")

        then:
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [a.jar.red.green, b.jar.red.green")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar", "a.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green")
    }

    def "task input file collection can include the output of artifact transform of project dependencies which takes the output of another transform as input parameter"() {
        def fixture = newInstantExecutionFixture()

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

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        instantRun(":resolve", "-PaContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")
    }

    def "task input file collection can include output of artifact transform of project dependencies when transform takes upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

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

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing c.jar using []")
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        instantRun(":resolve", "-PbContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskNotSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 2
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")
    }

    def "task input file collection can include output of artifact transform of external dependencies when transform takes upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies()

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing thing1-1.2.jar using []")
        outputContains("processing thing2-1.2.jar using []")
        outputContains("processing thing3-1.2.jar using [thing1-1.2.jar, thing2-1.2.jar]")
        outputContains("result = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        output.count("processing") == 0
        outputContains("result = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input artifact collection can include output of artifact transform of external dependencies when transform takes upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies()

        when:
        instantRun(":resolveArtifacts")

        then:
        output.count("processing") == 3
        outputContains("processing thing1-1.2.jar using []")
        outputContains("processing thing2-1.2.jar using []")
        outputContains("processing thing3-1.2.jar using [thing1-1.2.jar, thing2-1.2.jar]")
        outputContains("files = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing3-1.2.jar.green (group:thing3:1.2), thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")

        when:
        instantRun(":resolveArtifacts")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        output.count("processing") == 0
        outputContains("files = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing3-1.2.jar.green (group:thing3:1.2), thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")
    }

    private void setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies() {
        def dep1 = withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        def dep2 = withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing3", "1.2")).dependsOn(dep1).dependsOn(dep2).publish()

        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        buildFile << """
            dependencies {
                implementation 'group:thing3:1.2'
            }

            repositories {
                maven { url = '${mavenRepo.uri}' }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/13245")
    def "task input file collection can include output of artifact transform of project dependencies when transform takes transformed upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()

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

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 6
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("processing b.jar.red using []")
        outputContains("processing c.jar.red using []")
        outputContains("processing a.jar.red using [b.jar.red, c.jar.red]")
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")

        when:
        instantRun(":resolve", "-PbContent=changed")

        then:
        fixture.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskNotSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 3
        outputContains("processing b.jar")
        outputContains("processing b.jar.red using []")
        outputContains("processing a.jar.red using [b.jar.red, c.jar.red]")
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13245")
    def "task input file collection can include output of artifact transform of external dependencies when transform takes transformed upstream artifacts"() {
        def fixture = newInstantExecutionFixture()

        def dep1 = withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        def dep2 = withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing3", "1.2")).dependsOn(dep1).dependsOn(dep2).publish()

        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()

        buildFile << """
            dependencies {
                implementation 'group:thing3:1.2'
            }
            repositories {
                maven { url = '${mavenRepo.uri}' }
            }
        """

        when:
        instantRun(":resolve")

        then:
        output.count("processing") == 6
        outputContains("processing thing1-1.2.jar")
        outputContains("processing thing2-1.2.jar")
        outputContains("processing thing2-1.2.jar")
        outputContains("processing thing1-1.2.jar.red using []")
        outputContains("processing thing2-1.2.jar.red using []")
        outputContains("processing thing3-1.2.jar.red using [thing1-1.2.jar.red, thing2-1.2.jar.red]")
        outputContains("result = [thing3-1.2.jar.red.green, thing1-1.2.jar.red.green, thing2-1.2.jar.red.green]")

        when:
        instantRun(":resolve")

        then: // everything is up-to-date
        fixture.assertStateLoaded()
        output.count("processing") == 0
        outputContains("result = [thing3-1.2.jar.red.green, thing1-1.2.jar.red.green, thing2-1.2.jar.red.green]")
    }

    def "reports failure to transform prebuilt file dependency"() {
        settingsFile << """
            include 'a'
        """
        setupBuildWithColorTransformAction()
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    throw new RuntimeException("broken: \${input.name}")
                }
            }

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
        def fixture = newInstantExecutionFixture()

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateStored() // transform spec is stored
        output.count("processing") == 3
        outputContains("processing root.blue")
        outputContains("processing a.jar")
        outputContains("processing a.blue")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform root.blue to match attributes {artifactType=blue, color=green}.")
            it.assertHasCause("Failed to transform a.jar (project :a) to match attributes {artifactType=jar, color=green}.")
            it.assertHasCause("Failed to transform a.blue to match attributes {artifactType=blue, color=green}.")
        }
        failure.assertHasFailures(1)

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateLoaded()
        output.count("processing") == 2
        outputContains("processing root.blue")
        outputContains("processing a.jar")
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        // TODO - is missing context exception explaining which configuration could not be resolved
        // TODO - currently runs transforms for file and external dependencies sequentially and stops on first failure. Should instead run all transforms in parallel
        // TODO - stops on first failure, should collect all failures
    }

    def "reports failure to transform project dependency"() {
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransformAction()
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    throw new RuntimeException("broken: \${input.name}")
                }
            }

            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """

        def fixture = newInstantExecutionFixture()

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateStored()
        output.count("processing") == 2
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform a.jar (project :a) to match attributes {artifactType=jar, color=green}.")
            it.assertHasCause("Failed to transform b.jar (project :b) to match attributes {artifactType=jar, color=green}.")
        }

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateLoaded()
        output.count("processing") == 2
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        // TODO - is missing context exception explaining which configuration could not be resolved
        // TODO - stops on first failure, should collect all failures
    }

    def "reports failure to transform external dependency"() {
        withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()

        setupBuildWithColorTransformAction()

        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    throw new RuntimeException("broken: \${input.name}")
                }
            }

            repositories {
                maven { url = '${mavenRepo.uri}' }
            }

            dependencies {
                implementation 'group:thing1:1.2'
                implementation 'group:thing2:1.2'
            }
        """

        def fixture = newInstantExecutionFixture()

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateStored()
        output.count("processing") == 2
        outputContains("processing thing1-1.2.jar")
        outputContains("processing thing2-1.2.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform thing1-1.2.jar (group:thing1:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
            it.assertHasCause("Failed to transform thing2-1.2.jar (group:thing2:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
        }

        when:
        instantFails(":resolve")

        then:
        fixture.assertStateLoaded()
        output.count("processing") == 1
        outputContains("processing thing1-1.2.jar")
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        // TODO - is missing context exception explaining which configuration could not be resolved
        // TODO - currently runs transforms for file and external dependencies sequentially and stops on first failure. Should instead run all transforms in parallel
        // TODO - stops on first failure, should collect all failures
    }

    @Ignore("wip")
    def 'transform action is re-executed when input artifact changes'() {
        given:
        buildKotlinFile '''

abstract class Summarize : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        println("Transforming ${inputFile.name}...")
        outputs.file("${inputFile.nameWithoutExtension}-summary.txt").run {
            writeText("${inputFile.name}: ${inputFile.length()}")
        }
    }
}

val summarized = Attribute.of("summarized", Boolean::class.javaObjectType)
dependencies {
    attributesSchema {
        attribute(summarized)
    }
    artifactTypes.create("txt") {
        attributes.attribute(summarized, false)
    }
    registerTransform(Summarize::class) {
        from.attribute(summarized, false)
        to.attribute(summarized, true)
    }
}

val sourceFiles by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val summarizedFiles by configurations.creating {
    extendsFrom(sourceFiles)
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(summarized, true)
    }
}

abstract class CombineSummaries : DefaultTask() {

    @get:InputFiles
    abstract val inputSummaries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun summarize() {
        outputFile.get().asFile.run {
            parentFile.mkdirs()
            writeText(summaryString())
        }
    }

    private
    fun summaryString() = inputSummaries.files.joinToString(separator = "\\n") { it.readText() }
}

tasks.register<CombineSummaries>("summarize") {
    inputSummaries.from(summarizedFiles)
    outputFile.set(layout.buildDirectory.file("summary.txt"))
}

dependencies {
    sourceFiles(files("input.txt"))
}
'''
        def inputFile = file('input.txt').tap { write("the input file") }
        def outputFile = file('build/summary.txt')
        def expectedOutput = "input.txt: ${inputFile.length()}"
        def instant = newInstantExecutionFixture()

        when:
        instantRun 'summarize'

        then:
        instant.assertStateStored()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'

        when: 'input file changes'
        inputFile.text = inputFile.text.reverse()
        instantRun 'summarize'

        then:
        instant.assertStateLoaded()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'
    }
}
