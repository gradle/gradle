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

package org.gradle.configurationcache

import org.gradle.integtests.resolve.transform.ArtifactTransformTestFixture
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue

class ConfigurationCacheDependencyResolutionIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ArtifactTransformTestFixture {
    @Rule
    HttpServer httpServer = new HttpServer()
    def remoteRepo = new MavenHttpRepository(httpServer, mavenRepo)

    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def setupBuildWithEachDependencyType() {
        httpServer.start()
        taskTypeWithOutputFileProperty()

        remoteRepo.module("group", "lib1", "6500").publish().allowAll()

        createDirs("a", "b")
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                group = 'test'
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.systemProperty("\${project.name}Content").orElse("content")
                    output = layout.buildDirectory.file("\${project.name}.out")
                }
                configurations.default.outgoing.artifact(producer.output)
            }
            repositories {
                maven { url = uri('${remoteRepo.uri}') }
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
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithEachDependencyType()
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            task resolve(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """

        given:
        configurationCacheRun(":resolve")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")

        when:
        configurationCacheRun(":resolve", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
    }

    def "task input artifact collection can include project dependencies, external dependencies, prebuilt file dependencies and task output file dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithEachDependencyType()
        taskTypeLogsArtifactCollectionDetails()

        buildFile << """
            task resolve(type: ShowArtifactCollection) {
                collection = configurations.implementation.incoming.artifacts
            }
        """

        given:
        configurationCacheRun(":resolve")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing (a.thing), b.thing (b.thing), a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
        outputContains("variants = [{artifactType=thing}, {artifactType=thing}, {artifactType=out}, {artifactType=out}, {artifactType=jar, org.gradle.status=release}]")
        outputContains("variant capabilities = [[], [], [capability group='test', name='a', version='unspecified'], [capability group='test', name='b', version='unspecified'], [capability group='group', name='lib1', version='6500']]")

        when:
        configurationCacheRun(":resolve", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":additionalFile", ":resolve")
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":additionalFile")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("files = [a.thing, b.thing, a.out, b.out, lib1-6500.jar]")
        outputContains("artifacts = [a.thing (a.thing), b.thing (b.thing), a.out (project :a), b.out (project :b), lib1-6500.jar (group:lib1:6500)]")
        outputContains("variants = [{artifactType=thing}, {artifactType=thing}, {artifactType=out}, {artifactType=out}, {artifactType=jar, org.gradle.status=release}]")
        outputContains("variant capabilities = [[], [], [capability group='test', name='a', version='unspecified'], [capability group='test', name='b', version='unspecified'], [capability group='group', name='lib1', version='6500']]")
    }

    def "task input property can include mapped configuration elements that contain project dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        taskTypeWithOutputFileProperty()
        taskTypeWithInputListProperty()

        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'"""

        buildFile << """
            subprojects {
                configurations { create("default") }
                task producer(type: FileProducer) {
                    content = providers.systemProperty("\${project.name}Content").orElse("0")
                    output = layout.buildDirectory.file("\${project.name}.out")
                }
                configurations.default.outgoing.artifact(producer.output)
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
        configurationCacheRun(":resolve")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":resolve")
        file('out.txt').text == "10,10"

        when:
        configurationCacheRun(":resolve", "-DaContent=2")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskNotSkipped(":resolve")
        file('out.txt').text == "12,10"
    }

    def setupBuildWithArtifactTransformOfProjectDependencies() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """

        setupBuildWithColorTransformImplementation()

        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }
        """
    }

    def "task input file collection can include the output of artifact transform of project dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformOfProjectDependencies()

        when:
        configurationCacheRun(":resolve")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        configurationCacheRun(":resolve", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        assertTransformed("a.jar")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        outputContains("result = [a.jar.green, b.jar.green]")
    }

    def "task input artifact collection can include the output of artifact transform of project dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformOfProjectDependencies()

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        assertTransformed("a.jar", "b.jar")
        outputContains("files = [a.jar.green, b.jar.green]")
        outputContains("artifacts = [a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=jar, color=green}, {artifactType=jar, color=green}]")

        when:
        configurationCacheRun(":resolveArtifacts")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("files = [a.jar.green, b.jar.green]")
        outputContains("artifacts = [a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=jar, color=green}, {artifactType=jar, color=green}]")

        when:
        configurationCacheRun(":resolveArtifacts", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":b:producer", ":resolveArtifacts")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar")
        outputContains("files = [a.jar.green, b.jar.green]")
        outputContains("artifacts = [a.jar.green (project :a), b.jar.green (project :b)]")
        outputContains("variants = [{artifactType=jar, color=green}, {artifactType=jar, color=green}]")
    }

    def setupBuildWithArtifactTransformsOfExternalDependencies() {
        httpServer.start()
        withColorVariants(remoteRepo.module("group", "thing1", "1.2")).publish().allowAll()
        withColorVariants(remoteRepo.module("group", "thing2", "1.2")).publish().allowAll()

        setupBuildWithColorTransformImplementation()

        buildFile << """
            repositories {
                maven {
                    url = uri('${remoteRepo.uri}')
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
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfExternalDependencies()

        when:
        configurationCacheRun(":resolve")

        then:
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")

        when:
        configurationCacheRun(":resolve")

        then:
        configurationCache.assertStateLoaded()
        assertTransformed()
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input artifact collection can include the output of artifact transform of external dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfExternalDependencies()

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        outputContains("files = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        configurationCache.assertStateLoaded()
        assertTransformed()
        outputContains("files = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")
    }

    def "many tasks in the same project can consume the output of transform of external dependencies"() {
        setupBuildWithArtifactTransformsOfExternalDependencies()
        buildFile << """
            for (i in 0..5) {
                task "resolve\$i" {
                    def view = configurations.implementation.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    inputs.files view
                    doLast {
                        println "result = \${view.files.name}"
                    }
                }
            }
        """
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun(":resolve0", ":resolve1", ":resolve2", ":resolve3", ":resolve4")

        then:
        fixture.assertStateStored()
        assertTransformed("thing1-1.2.jar", "thing2-1.2.jar")
        output.count("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]") == 5

        when:
        configurationCacheRun(":resolve0", ":resolve1", ":resolve2", ":resolve3", ":resolve4")

        then:
        fixture.assertStateLoaded()
        assertTransformed()
        output.count("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]") == 5
    }

    def setupBuildWithArtifactTransformsOfPrebuiltFileDependencies() {
        setupBuildWithColorTransformImplementation()

        buildFile << """
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files('root.blue')
            }
        """
        file('root.blue') << 'root'
    }

    def "task input file collection can include the output of artifact transforms of prebuilt file dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfPrebuiltFileDependencies()

        when:
        configurationCacheRun(":resolve")

        then:
        assertTransformed("root.blue")
        outputContains("result = [root.blue.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        assertTransformed()
        outputContains("result = [root.blue.green]")
    }

    def "task input artifact collection can include the output of artifact transforms of prebuilt file dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfPrebuiltFileDependencies()

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        assertTransformed("root.blue")
        outputContains("files = [root.blue.green]")
        outputContains("artifacts = [root.blue.green (root.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}]")

        when:
        configurationCacheRun(":resolveArtifacts")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        assertTransformed()
        outputContains("files = [root.blue.green]")
        outputContains("artifacts = [root.blue.green (root.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}]")
    }

    def setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs() {
        createDirs("a")
        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """

        setupBuildWithColorTransformImplementation()

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
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs()

        when:
        configurationCacheRun(":resolve")

        then:
        assertTransformed("root.blue", "root.additional.blue", "a.additional.blue", "a.jar")
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":a:additionalFile", ":resolve")
        result.assertTaskOrder(":additionalFile", ":resolve")
        assertTransformed()
        outputContains("result = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13200")
    def "task input artifact collection can include the output of artifact transforms of file dependencies that include task outputs"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfFileDependenciesThatContainTaskOutputs()

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        assertTransformed("root.blue", "root.additional.blue", "a.additional.blue", "a.jar")
        outputContains("files = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
        outputContains("artifacts = [root.additional.blue.green (root.additional.blue), root.blue.green (root.blue), a.jar.green (project :a), a.additional.blue.green (a.additional.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")

        when:
        configurationCacheRun(":resolveArtifacts")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolveArtifacts")
        result.assertTaskOrder(":a:additionalFile", ":resolveArtifacts")
        result.assertTaskOrder(":additionalFile", ":resolveArtifacts")
        assertTransformed()
        outputContains("files = [root.additional.blue.green, root.blue.green, a.jar.green, a.additional.blue.green]")
        outputContains("artifacts = [root.additional.blue.green (root.additional.blue), root.blue.green (root.blue), a.jar.green (project :a), a.additional.blue.green (a.additional.blue)]")
        outputContains("variants = [{artifactType=blue, color=green}, {artifactType=blue, color=green}, {artifactType=jar, color=green}, {artifactType=blue, color=green}]")
    }

    def "task input file collection can include the output of chained artifact transform of project dependencies"() {
        def configurationCache = newConfigurationCacheFixture()

        createDirs("a", "b")
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
        configurationCacheRun(":resolve")

        then:
        assertTransformed("a.jar", "a.jar.red", "b.jar", "b.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed()
        outputContains("result = [a.jar.red.green, b.jar.red.green")

        when:
        configurationCacheRun(":resolve", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskNotSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        assertTransformed("a.jar", "a.jar.red")
        outputContains("result = [a.jar.red.green, b.jar.red.green")
    }

    def "task input file collection can include the output of artifact transform of project dependencies which takes the output of another transform as input parameter"() {
        def configurationCache = newConfigurationCacheFixture()

        createDirs("a", "b")
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
        configurationCacheRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing a.jar to make red")
        outputContains("processing a.jar using [a.jar.red]")
        outputContains("processing b.jar using [a.jar.red]")
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green]")

        when:
        configurationCacheRun(":resolve", "-DaContent=changed")

        then:
        configurationCache.assertStateLoaded()
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
        def configurationCache = newConfigurationCacheFixture()

        createDirs("a", "b", "c")
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
        configurationCacheRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing c.jar using []")
        outputContains("processing b.jar using []")
        outputContains("processing a.jar using [b.jar, c.jar]")
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.green, b.jar.green, c.jar.green]")

        when:
        configurationCacheRun(":resolve", "-DbContent=changed")

        then:
        configurationCache.assertStateLoaded()
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
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies()

        when:
        configurationCacheRun(":resolve")

        then:
        output.count("processing") == 3
        outputContains("processing thing1-1.2.jar using []")
        outputContains("processing thing2-1.2.jar using []")
        outputContains("processing thing3-1.2.jar using [thing1-1.2.jar, thing2-1.2.jar]")
        outputContains("result = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")

        when:
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        output.count("processing") == 0
        outputContains("result = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
    }

    def "task input artifact collection can include output of artifact transform of external dependencies when transform takes upstream artifacts"() {
        def configurationCache = newConfigurationCacheFixture()

        setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies()

        when:
        configurationCacheRun(":resolveArtifacts")

        then:
        output.count("processing") == 3
        outputContains("processing thing1-1.2.jar using []")
        outputContains("processing thing2-1.2.jar using []")
        outputContains("processing thing3-1.2.jar using [thing1-1.2.jar, thing2-1.2.jar]")
        outputContains("files = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing3-1.2.jar.green (group:thing3:1.2), thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")

        when:
        configurationCacheRun(":resolveArtifacts")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        output.count("processing") == 0
        outputContains("files = [thing3-1.2.jar.green, thing1-1.2.jar.green, thing2-1.2.jar.green]")
        outputContains("artifacts = [thing3-1.2.jar.green (group:thing3:1.2), thing1-1.2.jar.green (group:thing1:1.2), thing2-1.2.jar.green (group:thing2:1.2)]")
        outputContains("variants = [{artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}, {artifactType=jar, color=green, org.gradle.status=release}]")
    }

    private void setupBuildWithArtifactTransformsOfExternalDependenciesThatTakeUpstreamDependencies() {
        httpServer.start()
        def dep1 = withColorVariants(remoteRepo.module("group", "thing1", "1.2")).publish().allowAll()
        def dep2 = withColorVariants(remoteRepo.module("group", "thing2", "1.2")).publish().allowAll()
        withColorVariants(remoteRepo.module("group", "thing3", "1.2")).dependsOn(dep1).dependsOn(dep2).publish().allowAll()

        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        buildFile << """
            dependencies {
                implementation 'group:thing3:1.2'
            }

            repositories {
                maven { url = '${remoteRepo.uri}' }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/13245")
    def "task input file collection can include output of artifact transform of project dependencies when transform takes transformed upstream artifacts"() {
        def configurationCache = newConfigurationCacheFixture()

        createDirs("a", "b", "c")
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
        configurationCacheRun(":resolve")

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
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":a:producer", ":resolve")
        result.assertTaskOrder(":b:producer", ":resolve")
        result.assertTaskOrder(":c:producer", ":resolve")
        result.assertTaskSkipped(":a:producer")
        result.assertTaskSkipped(":b:producer")
        result.assertTaskSkipped(":c:producer")
        output.count("processing") == 0
        outputContains("result = [a.jar.red.green, b.jar.red.green, c.jar.red.green]")

        when:
        configurationCacheRun(":resolve", "-DbContent=changed")

        then:
        configurationCache.assertStateLoaded()
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
        def configurationCache = newConfigurationCacheFixture()

        httpServer.start()
        def dep1 = withColorVariants(remoteRepo.module("group", "thing1", "1.2")).publish().allowAll()
        def dep2 = withColorVariants(remoteRepo.module("group", "thing2", "1.2")).publish().allowAll()
        withColorVariants(remoteRepo.module("group", "thing3", "1.2")).dependsOn(dep1).dependsOn(dep2).publish().allowAll()

        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()

        buildFile << """
            dependencies {
                implementation 'group:thing3:1.2'
            }
            repositories {
                maven { url = '${remoteRepo.uri}' }
            }
        """

        when:
        configurationCacheRun(":resolve")

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
        configurationCacheRun(":resolve")

        then: // everything is up-to-date
        configurationCache.assertStateLoaded()
        output.count("processing") == 0
        outputContains("result = [thing3-1.2.jar.red.green, thing1-1.2.jar.red.green, thing2-1.2.jar.red.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/14513")
    def "task input file collection can include transformed outputs of file collection containing transform outputs"() {
        setupBuildWithArtifactTransformOfProjectDependencies()
        buildFile << """
            abstract class MakeRed implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "converting \${input.name} to red"
                    assert input.file
                    def output = outputs.file(input.name + ".red")
                    output.text = input.text + ".red"
                }
            }

            dependencies {
                artifactTypes {
                    green {
                        attributes.attribute(color, 'green')
                    }
                }
                registerTransform(MakeRed) {
                    from.attribute(color, 'green')
                    to.attribute(color, 'red')
                }
            }

            configurations {
                transformed
            }

            def intermediateFiles = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            dependencies {
                transformed files(intermediateFiles)
            }

            def transformedFiles = configurations.transformed.incoming.artifactView {
                attributes.attribute(color, 'red')
            }.files

            task resolveTransformed(type: ShowFileCollection) {
                files.from(transformedFiles)
            }
        """
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolveTransformed")

        then:
        fixture.assertStateStored()
        outputContains("processing [a.jar]")
        outputContains("processing [b.jar]")
        outputContains("converting a.jar.green to red")
        outputContains("converting b.jar.green to red")
        outputContains("result = [a.jar.green.red, b.jar.green.red]")

        when:
        configurationCacheRun("resolveTransformed")

        then:
        fixture.assertStateLoaded()
        outputDoesNotContain("processing")
        outputDoesNotContain("converting")
        outputContains("result = [a.jar.green.red, b.jar.green.red]")
    }

    @Issue("https://github.com/gradle/gradle/issues/23116")
    def "task inputs can include an external artifact that is transformed at configuration time"() {
        withColorVariants(mavenRepo.module("org.test", "test", "1.0").withModuleMetadata()).publish()
        setupBuildWithColorTransformImplementation()
        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                registerTransform(MakeGreen) {
                    from.attribute(color, 'green')
                    to.attribute(color, 'chartreuse')
                }
                implementation "org.test:test:1.0"
            }

            // To trigger the issue:
            // 1. serialize a task that uses the transformed artifact
            // 2. serialize a task whose serialization triggers execution of that transform
            // 3. serialize another task that uses the transformed artifact
            // In addition, the 1st and 3rd tasks need to resolve different variants that share the same transform

            def files1 = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'green')
            }.files

            task usesFiles1 {
                doLast {
                    println files1*.name
                }
            }

            def files2 = configurations.implementation.incoming.artifactView {
                attributes.attribute(color, 'chartreuse')
            }.files

            task resolveFilesWhenSerialized {
                def input = provider { files2*.name }
                doLast {
                    println input.get()
                }
            }

            task usesFiles2 {
                doLast {
                    println files2*.name
                }
            }
        """

        when:
        configurationCacheRun("usesFiles1", "resolveFilesWhenSerialized", "usesFiles2")

        then:
        assertTransformed("test-1.0.jar", "test-1.0.jar.green")

        when:
        configurationCacheRun("usesFiles1", "resolveFilesWhenSerialized", "usesFiles2")

        then:
        assertTransformed()
    }

    def "buildSrc output may require transform output"() {
        withColorVariants(mavenRepo.module("test", "test", "12")).publish()

        createDirs("buildSrc/producer")
        file("buildSrc/settings.gradle") << """
            include 'producer'
        """
        def buildSrcBuildFile = file("buildSrc/build.gradle")
        setupBuildWithColorTransformImplementation(buildSrcBuildFile)
        buildSrcBuildFile << """
            repositories {
                maven { url = '${mavenRepo.uri}' }
            }
            dependencies {
                artifactTypes {
                    blue {
                        attributes.attribute(color, 'blue')
                    }
                }
            }
            dependencies { implementation project(':producer') }
            dependencies { implementation files('thing.blue') }
            dependencies { implementation 'test:test:12' }
            jar.dependsOn(resolve)
        """

        file("buildSrc/thing.blue").createFile()
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun()

        then:
        fixture.assertStateStored()
        result.assertTaskExecuted(":buildSrc:producer:producer")
        result.assertTaskExecuted(":buildSrc:resolve")
        result.assertTaskExecuted(":help")
        assertTransformed("producer.jar", "test-12.jar", "thing.blue")

        when:
        configurationCacheRun()

        then:
        fixture.assertStateLoaded()
        result.assertTasksExecuted(":help")
        assertTransformed()
    }

    def "reports failure to transform prebuilt file dependency"() {
        createDirs("a")
        settingsFile << """
            include 'a'
        """
        setupBuildWithColorTransform()
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
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheFails(":resolve", "--continue")

        then:
        configurationCache.assertStateStored() // transform spec is stored
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
        configurationCacheFails(":resolve")

        then:
        configurationCache.assertStateLoaded()
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
    }

    def "reports failure to transform project dependency"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform()
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

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheFails(":resolve")

        then:
        configurationCache.assertStateStored()
        output.count("processing") == 2
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform a.jar (project :a) to match attributes {artifactType=jar, color=green}.")
            // TODO - should collect all failures rather than stopping on first failure
        }

        when:
        configurationCacheFails(":resolve")

        then:
        configurationCache.assertStateLoaded()
        output.count("processing") == 2
        outputContains("processing a.jar")
        outputContains("processing b.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform a.jar (project :a) to match attributes {artifactType=jar, color=green}.")
            // TODO - should collect all failures rather than stopping on first failure
        }
    }

    def "reports failure to transform external dependency"() {
        httpServer.start()
        withColorVariants(remoteRepo.module("group", "thing1", "1.2")).publish().allowAll()
        withColorVariants(remoteRepo.module("group", "thing2", "1.2")).publish().allowAll()

        setupBuildWithColorTransform()

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
                maven { url = '${remoteRepo.uri}' }
            }

            dependencies {
                implementation 'group:thing1:1.2'
                implementation 'group:thing2:1.2'
            }
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheFails(":resolve")

        then:
        configurationCache.assertStateStored()
        output.count("processing") == 2
        outputContains("processing thing1-1.2.jar")
        outputContains("processing thing2-1.2.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform thing1-1.2.jar (group:thing1:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
            it.assertHasCause("Failed to transform thing2-1.2.jar (group:thing2:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
        }

        when:
        configurationCacheFails(":resolve")

        then:
        configurationCache.assertStateLoaded()
        output.count("processing") == 2
        outputContains("processing thing1-1.2.jar")
        outputContains("processing thing2-1.2.jar")
        failure.assertHasFailure("Execution failed for task ':resolve'.") {
            it.assertHasCause("Failed to transform thing1-1.2.jar (group:thing1:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
            it.assertHasCause("Failed to transform thing2-1.2.jar (group:thing2:1.2) to match attributes {artifactType=jar, color=green, org.gradle.status=release}.")
        }
    }

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
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'summarize'

        then:
        configurationCache.assertStateStored()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'

        when: 'input file changes'
        inputFile.text = inputFile.text.reverse()
        configurationCacheRun 'summarize'

        then:
        configurationCache.assertStateLoaded()
        outputFile.text == expectedOutput
        outputContains 'Transforming input.txt...'
        result.assertTaskExecuted ':summarize'
    }

    def 'can use ListProperty of ComponentArtifactIdentifier as task input'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildScript '''
            plugins {
                id 'java-library'
            }

            dependencies {
                implementation(gradleApi())
            }

            @CacheableTask
            abstract class PrintArtifactIds extends DefaultTask {

                @Input
                abstract ListProperty<ComponentArtifactIdentifier> getArtifactIds()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                def printArtifactIds() {
                    outputFile.get().asFile.text = artifactIds.get().join('\\n')
                }
            }

            tasks.register('printArtifactIds', PrintArtifactIds) {
                artifactIds.addAll(
                  configurations
                      .compileClasspath
                      .incoming.artifactView {
                          attributes.attribute(Attribute.of('artifactType', String), 'jar')
                      }.artifacts.resolvedArtifacts.map { artifacts ->
                          artifacts.collect { it.id }
                      }
                )
                outputFile = layout.buildDirectory.file('ids.txt')
            }
        '''

        when:
        configurationCacheRun ':printArtifactIds', '-s'

        then:
        configurationCache.assertStateStored()

        and:
        file('build/ids.txt').text.contains('(Gradle API)')

        when:
        configurationCacheRun ':printArtifactIds'

        then:
        configurationCache.assertStateLoaded()

        and:
        result.assertTaskSkipped ':printArtifactIds'
    }
}
