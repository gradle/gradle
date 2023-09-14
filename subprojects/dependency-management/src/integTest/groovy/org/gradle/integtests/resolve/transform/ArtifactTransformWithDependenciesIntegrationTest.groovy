/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import com.google.common.collect.Comparators
import com.google.common.collect.ImmutableSortedMultiset
import com.google.common.collect.Iterables
import com.google.common.collect.Multiset
import groovy.test.NotYetImplemented
import groovy.transform.Canonical
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers
import spock.lang.Issue

import javax.annotation.Nonnull
import java.util.regex.Pattern

@Requires(IntegTestPreconditions.NotParallelExecutor)
class ArtifactTransformWithDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest implements ArtifactTransformTestFixture {

    def setup() {
        settingsFile << """
            rootProject.name = 'transform-deps'
            include 'common', 'lib', 'app'
        """
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.24")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.25")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("junit", "junit", "4.11"))
            .dependsOn("hamcrest", "hamcrest-core", "1.3")
            .publish()
            .allowAll()
        withColorVariants(mavenHttpRepo.module("hamcrest", "hamcrest-core", "1.3")).publish().allowAll()
    }

    void setupBuildWithNoSteps(@DelegatesTo(Builder) Closure cl = {}) {
        setupBuildWithColorAttributes(buildFile, cl)
        setupTransformerTypes()
        buildFile << """

allprojects {
    repositories {
        maven {
            url = '${mavenHttpRepo.uri}'
            metadataSources { gradleMetadata() }
        }
    }

    dependencies {
        artifactTypes {
            jar {
                attributes.attribute(color, 'blue')
            }
        }
    }

    task resolveGreen(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(color, 'green') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/green"
    }
}

project(':common') {
}

project(':lib') {
    dependencies {
        implementation providers.gradleProperty('useOldDependencyVersion').map { 'org.slf4j:slf4j-api:1.7.24' }.orElse('org.slf4j:slf4j-api:1.7.25')
        implementation project(':common')
    }
}

project(':app') {
    dependencies {
        implementation 'junit:junit:4.11'
        implementation project(':lib')
    }
}

"""
    }

    void setupTransformerTypes() {
        buildFile << """
            abstract class TestTransform implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getTransformName()
                    void setTransformName(String name)
                }

                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "\${parameters.transformName} received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"
                    assert inputArtifactDependencies.every { it.exists() }

                    def output = outputs.file(input.name + ".txt")
                    def workspace = output.parentFile
                    assert workspace.directory && workspace.list().length == 0
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                }
            }

            abstract class SimpleTransform implements TransformAction<TransformParameters.None> {

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    def workspace = output.parentFile
                    assert workspace.directory && workspace.list().length == 0
                    println "Transforming without dependencies \${input.name} to \${output.name}"
                    if (input.name == System.getProperty("failTransformOf")) {
                        throw new RuntimeException("Cannot transform")
                    }
                    output.text = String.valueOf(input.length())
                }
            }
        """
    }

    void setupBuildWithSingleStep() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        registerTransform(TestTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Single step transform'
            }
        }
    }
}
"""
    }

    void setupBuildWithMultipleGraphsPerProject() {
        setupBuildWithColorAttributes()
        setupTransformerTypes()

        buildFile << """
            allprojects {
                repositories {
                    maven {
                        url = '${mavenHttpRepo.uri}'
                        metadataSources { gradleMetadata() }
                    }
                }
                configurations {
                    testImplementation {
                        extendsFrom implementation
                        assert canBeResolved
                        canBeConsumed = false
                        attributes.attribute(color, 'blue')
                    }
                }
                task resolveTest(type: ShowFileCollection) {
                    def view = configurations.testImplementation.incoming.artifactView {
                        attributes.attribute(color, 'green')
                    }.files
                    files.from(view)
                }
            }
        """
    }

    void setupSingleStepTransform() {
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(TestTransform) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            transformName = 'Single step transform'
                        }
                    }
                }
            }
        """
    }

    void setupTransformWithNoDependencies() {
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(SimpleTransform) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }
        """
    }

    void setupBuildWithFirstStepThatDoesNotUseDependencies() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        //Multi step transform, without dependencies at step 1
        registerTransform(SimpleTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'yellow')
        }
        registerTransform(TestTransform) {
            from.attribute(color, 'yellow')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Transform step 2'
            }
        }
    }
}
"""
    }

    void setupBuildWithTwoSteps() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        // Multi step transform
        registerTransform(TestTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'yellow')
            parameters {
                transformName = 'Transform step 1'
            }
        }
        registerTransform(TestTransform) {
            from.attribute(color, 'yellow')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Transform step 2'
            }
        }
    }
}
"""
    }

    def "transform can access artifact dependencies as a set of files when using ArtifactView"() {
        given:
        setupBuildWithSingleStep()

        when:
        executer.withArgument("--parallel")
        run ":app:resolveGreen"

        then:
        output.count('Transforming') == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }

    def "transform can access file dependencies as a set of files when using ArtifactView"() {
        given:
        setupBuildWithSingleStep()
        buildFile << """
project(':common') {
    dependencies {
        implementation files("otherLib.jar")
    }
}
"""

        when:
        executer.withArgument("--parallel")
        run "common:resolveGreen"

        then:
        output.count('Transforming') == 1
        output.contains('Single step transform received dependencies files [] for processing otherLib.jar')
    }

    def "transform can access artifact dependencies as a set of files when using ArtifactView, even if first step did not use dependencies"() {
        given:
        setupBuildWithFirstStepThatDoesNotUseDependencies()

        when:
        executer.withArgument("--parallel")
        run "app:resolveGreen"

        then:
        assertTransformationsExecuted(
            simpleTransform('common.jar'),
            simpleTransform('hamcrest-core-1.3.jar'),
            simpleTransform('lib.jar'),
            simpleTransform('junit-4.11.jar'),
            simpleTransform('slf4j-api-1.7.25.jar'),
            transformStep2('common.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep2('slf4j-api-1.7.25.jar')
        )
    }

    def "transform can access artifact dependencies, in previous transform step, as set of files when using ArtifactView"() {
        given:
        setupBuildWithTwoSteps()

        when:
        executer.withArgument("--parallel")
        run "app:resolveGreen"

        then:
        assertTransformationsExecuted(
            transformStep1('common.jar'),
            transformStep1('hamcrest-core-1.3.jar'),
            transformStep1('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep1('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep1('slf4j-api-1.7.25.jar'),
            transformStep2('common.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep2('slf4j-api-1.7.25.jar')
        )
    }

    def "transform of project artifact can consume different transform of external artifact as dependency"() {
        given:
        mavenHttpRepo.module("test", "test", "1.2")
            .adhocVariants()
            .variant('runtime', [color: 'blue'])
            .variant('test', [color: 'orange'])
            .withModuleMetadata()
            .publish()
            .allowAll()

        settingsFile << "include 'a', 'b'"
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
                configurations.outgoing.outgoing.variants {
                    additional {
                        attributes {
                            attribute(color, 'purple')
                            artifact(producer.output)
                        }
                    }
                }
                dependencies {
                    registerTransform(ExternalTransform) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'purple')
                        parameters {
                            transformName = 'external'
                        }
                    }
                    registerTransform(LocalTransform) {
                        from.attribute(color, 'purple')
                        to.attribute(color, 'green')
                        parameters {
                            transformName = 'local'
                        }
                    }
                }
            }
            project('a') {
                dependencies {
                    implementation "test:test:1.2"
                }
            }
            dependencies {
                implementation project('a')
            }

            interface Params extends TransformParameters {
                @Input
                Property<String> getTransformName()
            }

            abstract class ExternalTransform implements TransformAction<Params> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    println("transform external " + inputArtifact.get().asFile.name)
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".external")
                    output.text = "content"
                }
            }

            abstract class LocalTransform implements TransformAction<Params> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                void transform(TransformOutputs outputs) {
                    println("transform local " + inputArtifact.get().asFile.name + " using " + inputArtifactDependencies.files.name)
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".local")
                    output.text = "content"
                }
            }
        """

        when:
        run(":resolve")

        then:
        outputContains("transform external test-1.2.jar")
        outputContains("transform local a.jar using [test-1.2.jar.external]")
        outputContains("transform local test-1.2.jar.external using []")
        outputContains("result = [a.jar.local, test-1.2.jar.external.local]")

        when:
        run(":resolve")

        then:
        outputDoesNotContain("transform")
        outputContains("result = [a.jar.local, test-1.2.jar.external.local]")
    }

    def setupBuildWithTransformOfExternalDependencyThatUsesDifferentTransformForUpstreamDependencies() {
        def m1 = mavenHttpRepo.module("test", "test", "1.2")
            .adhocVariants()
            .variant('runtime', [color: 'blue'])
            .variant('test', [color: 'orange'])
            .withModuleMetadata()
            .publish()
            .allowAll()
        mavenHttpRepo.module("test", "test2", "1.5")
            .hasType("thing")
            .dependsOn(m1)
            .publish()
            .allowAll()
        mavenHttpRepo.module("test", "test3", "1.5")
            .hasType("thing")
            .dependsOn(m1)
            .publish()
            .allowAll()

        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                repositories {
                    maven { url = '${mavenHttpRepo.uri}' }
                }
                dependencies {
                    artifactTypes {
                        thing {
                            attributes.attribute(color, 'purple')
                        }
                    }
                    registerTransform(ExternalTransform) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'purple')
                        parameters {
                            transformName = 'external'
                        }
                    }
                    registerTransform(LocalTransform) {
                        from.attribute(color, 'purple')
                        to.attribute(color, 'green')
                        parameters {
                            transformName = 'local'
                        }
                    }
                }
            }

            interface Params extends TransformParameters {
                @Input
                Property<String> getTransformName()
            }

            abstract class ExternalTransform implements TransformAction<Params> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    println("transform external " + inputArtifact.get().asFile.name)
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".external")
                    output.text = "content"
                }
            }

            interface LocalParams extends Params {}

            abstract class LocalTransform implements TransformAction<LocalParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                void transform(TransformOutputs outputs) {
                    println("transform local " + inputArtifact.get().asFile.name + " using " + inputArtifactDependencies.files.name)
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".local")
                    output.text = "content"
                }
            }

            dependencies {
                implementation 'test:test2:1.5'
                implementation 'test:test3:1.5'
            }

            def view = configurations.resolver.incoming.artifactView {
                attributes.attribute(color, 'green')
                // NOTE: filter out the dependency to trigger the problem, so that the main thread, which holds the project lock, does not see and isolate the second transform while
                // queuing the transforms for execution
                // The problem can potentially also be triggered by including many direct dependencies so that the queued transforms start to execute before the main thread sees the second transform
                componentFilter { it instanceof ModuleComponentIdentifier && it.module != 'test' }
            }.artifacts
        """
    }

    def "file collection queried can contain the transform of external artifact can consume different transform of external artifact as dependency"() {
        given:
        setupBuildWithTransformOfExternalDependencyThatUsesDifferentTransformForUpstreamDependencies()

        buildFile << """
            resolveArtifacts.collection = view
        """

        when:
        run(":resolveArtifacts")

        then:
        output.count("transform") == 3
        outputContains("transform external test-1.2.jar")
        outputContains("transform local test2-1.5.thing using [test-1.2.jar.external]")
        outputContains("transform local test3-1.5.thing using [test-1.2.jar.external]")
        outputContains("artifacts = [test2-1.5.thing.local (test:test2:1.5), test3-1.5.thing.local (test:test3:1.5)]")

        when:
        run(":resolveArtifacts")

        then:
        outputDoesNotContain("transform")
        outputContains("artifacts = [test2-1.5.thing.local (test:test2:1.5), test3-1.5.thing.local (test:test3:1.5)]")
    }

    def "file collection queried during task graph calculation can contain the transform of external artifact can consume different transform of external artifact as dependency"() {
        given:
        setupBuildWithTransformOfExternalDependencyThatUsesDifferentTransformForUpstreamDependencies()

        buildFile << """
            resolveArtifacts.collection = view
            resolveArtifacts.dependsOn {
                view.forEach { println("artifact = " + it) }
                []
            }
        """

        when:
        run(":resolveArtifacts")

        then:
        output.count("transform") == 3
        output.count("transform external test-1.2.jar") == 1
        output.count("transform local test2-1.5.thing using [test-1.2.jar.external]") == 1
        output.count("transform local test3-1.5.thing using [test-1.2.jar.external]") == 1
        output.count("artifacts = [test2-1.5.thing.local (test:test2:1.5), test3-1.5.thing.local (test:test3:1.5)]") == 1

        when:
        run(":resolveArtifacts")

        then:
        outputDoesNotContain("transform")
        outputContains("artifacts = [test2-1.5.thing.local (test:test2:1.5), test3-1.5.thing.local (test:test3:1.5)]")
    }

    @Issue("https://github.com/gradle/gradle/issues/14529")
    def "transform of project artifact can consume transform of external artifact whose upstream dependency has been substituted with local project"() {
        given:
        def m1 = mavenRepo.module("test", "lib", "1.2").publish()
        mavenRepo.module("test", "lib2", "1.2").dependsOn(m1).publish()

        settingsFile << "include 'app', 'lib'"
        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()
        buildFile << """
            allprojects {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
            project(':lib') {
                // To trigger the substitution: use coordinates that conflict with the published library but which are newer
                group = 'test'
                version = '2.0'
            }
            project(':app') {
                dependencies {
                    // To trigger the subsitution: include paths to the other project via both a project dependency and external dependency
                    implementation "test:lib2:1.2"
                    implementation project(':lib')

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'red')
                        }
                    }
                }
                configurations.implementation.resolutionStrategy.dependencySubstitution.all {
                    // To trigger the substitution: include dependency substitution rule to include external dependencies in the execution graph calculation
                }

                tasks.resolveArtifacts.collection = configurations.implementation.incoming.artifactView {
                    attributes.attribute(color, 'green')
                    // To trigger the problem: exclude the local library from the result, so that only the execution node edges that are reachable via the external dependency are included in the graph
                    componentFilter { it instanceof ModuleComponentIdentifier }
                }.artifacts

            }
        """

        when:
        run(":app:resolveArtifacts")

        then:
        outputContains("processing [lib.jar]")
        outputContains("processing lib2-1.2.jar using [lib.jar.red]")
        outputContains("files = [lib2-1.2.jar.green]")

        when:
        run(":app:resolveArtifacts")

        then:
        outputDoesNotContain("processing")
        outputContains("files = [lib2-1.2.jar.green]")
    }

    def "transform of project artifact can consume upstream dependencies when accessed via ArtifactView even when artifacts of original configuration cannot be resolved"() {
        given:
        setupBuildWithColorAttributes()
        setupTransformerTypes()
        taskTypeLogsInputFileCollectionContent()

        buildFile << """
            def flavor = Attribute.of('flavor', String)

            allprojects {
                configurations {
                    outgoing.outgoing.variants {
                        one {
                            attributes.attribute(flavor, 'bland')
                            artifact(producer.output)
                        }
                        two {
                            attributes.attribute(flavor, 'cloying')
                        }
                    }
                }
                dependencies {
                    registerTransform(TestTransform) {
                        from.attribute(color, 'blue')
                        from.attribute(flavor, 'bland')
                        to.attribute(color, 'green')
                        to.attribute(flavor, 'tasty')
                        parameters {
                            transformName = 'Single step transform'
                        }
                    }
                }

                def view = configurations.resolver.incoming.artifactView {
                    attributes {
                        it.attribute(color, 'green')
                        it.attribute(flavor, 'tasty')
                    }
                }.files

                task resolveView(type: ShowFilesTask) {
                    inFiles.from(view)
                }

                task broken(type: ShowFilesTask) {
                    inFiles.from(configurations.resolver)
                }
            }

            project(':app') {
                dependencies {
                    implementation project(':lib')
                    // Needs to also include a file dependency to trigger the issue
                    implementation files('app.txt')
                }
            }

            project(':lib') {
                dependencies {
                    implementation project(':common')
                }
            }
        """

        when:
        fails("app:broken")

        then:
        failure.assertHasCause("The consumer was configured to find attribute 'color' with value 'blue'. However we cannot choose between the following variants of project :lib:")

        when:
        run("app:resolveView")

        then:
        assertTransformationsExecuted(
            singleStep("common.jar"),
            singleStep("lib.jar", "common.jar")
        )
        outputContains("result = [app.txt, lib.jar.txt, common.jar.txt]")

        when:
        run("app:resolveView")

        then:
        assertTransformationsExecuted()
        outputContains("result = [app.txt, lib.jar.txt, common.jar.txt]")
    }

    def "transform with changed set of dependencies are re-executed"() {
        given:
        setupBuildWithSingleStep()

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PuseOldDependencyVersion"

        then: // new version, should run
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.24.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.24.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PuseOldDependencyVersion"

        then: // no changes, should be up-to-date
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        assertTransformationsExecuted()
    }

    def "transform with changed project file dependencies content or path are re-executed"() {
        given:
        setupBuildWithSingleStep()

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out"

        then: // new path, should re-run
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // new name, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar", "-DcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()
    }

    def "can attach @PathSensitive(NONE) to dependencies property"() {
        given:
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        registerTransform(NoneTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}

abstract class NoneTransform implements TransformAction<TransformParameters.None> {
    @InputArtifactDependencies @PathSensitive(PathSensitivity.NONE)
    abstract FileCollection getInputArtifactDependencies()

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        println "Single step transform received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"

        def output = outputs.file(input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
    }
}

"""

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out"

        then: // new path, should skip consumer
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // new name, should skip consumer
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar", "-DcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()
    }

    def "can attach @#classpathAnnotation.simpleName to dependencies property"() {
        given:
        setupBuildWithNoSteps {
            produceJars()
        }
        buildFile << """
allprojects {
    dependencies {
        registerTransform(ClasspathTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}

abstract class ClasspathTransform implements TransformAction<TransformParameters.None> {
    @InputArtifactDependencies @${classpathAnnotation.simpleName}
    abstract FileCollection getInputArtifactDependencies()

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        println "Single step transform received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"

        def output = outputs.file(input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
    }
}

"""

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out"

        then: // new path, should skip consumer
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // new name, should skip consumer
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-DcommonOutputDir=out", "-DcommonFileName=common-blue.jar", "-DcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common-blue.jar')
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()

        where:
        classpathAnnotation << [Classpath, CompileClasspath]
    }

    def "transforms with different dependencies in multiple dependency graphs in different projects are executed"() {
        given:
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.26")).publish().allowAll()
        settingsFile << "include('app2')"
        setupBuildWithTwoSteps()
        buildFile << """
            project(':app2') {
                dependencies {
                    implementation 'junit:junit:4.11'
                    implementation 'org.slf4j:slf4j-api:1.7.26'
                    implementation project(':lib')
                }
            }
        """
        def hamcrest = 'hamcrest-core-1.3.jar'
        def junit411 = ['junit-4.11.jar': [hamcrest]]
        def common = 'common.jar'
        def slf4jOld = 'slf4j-api-1.7.25.jar'
        def slf4jNew = 'slf4j-api-1.7.26.jar'
        def libWithSlf4Old = ['lib.jar': [slf4jOld, common]]
        def libWithSlf4New = ['lib.jar': [slf4jNew, common]]

        when:
        run ":app:resolveGreen", ":app2:resolveGreen"

        then:
        assertTransformationsExecuted(
            transformStep1(common),
            transformStep2(common),
            transformStep1(hamcrest),
            transformStep2(hamcrest),
            transformStep1(junit411),
            transformStep2(junit411),

            transformStep1(slf4jOld),
            transformStep2(slf4jOld),

            transformStep1(libWithSlf4Old),
            transformStep2(libWithSlf4Old),

            transformStep1(slf4jNew),
            transformStep2(slf4jNew),

            transformStep1(libWithSlf4New),
            transformStep2(libWithSlf4New),
        )

        def outputLines = output.readLines()
        def app1Resolve = outputLines.indexOf("> Task :app:resolveGreen")
        def app2Resolve = outputLines.indexOf("> Task :app2:resolveGreen")
        def libTransformWithOldSlf4j = outputLines.indexOf("Transform step 1 received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar")
        def libTransformWithNewSlf4j = outputLines.indexOf("Transform step 1 received dependencies files [slf4j-api-1.7.26.jar, common.jar] for processing lib.jar")
        ![app1Resolve, app2Resolve, libTransformWithOldSlf4j, libTransformWithNewSlf4j].contains(-1)
        // scheduled transformations, executed before the resolve task
        assert libTransformWithOldSlf4j < app1Resolve
        assert libTransformWithNewSlf4j < app2Resolve
    }

    @Issue("https://github.com/gradle/gradle/issues/15536")
    def "transform of project dependency with different upstream dependencies in multiple dependency graphs in the same project are executed"() {
        given:
        setupBuildWithMultipleGraphsPerProject()
        setupSingleStepTransform()

        buildFile << """
            project(':app') {
                dependencies {
                    implementation project(':lib')
                    testImplementation 'org.slf4j:slf4j-api:1.7.25'
                }
            }
            project(':lib') {
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.24'
                }
            }
        """

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 4
        output.contains("result = [lib.jar.txt, slf4j-api-1.7.24.jar.txt]")
        output.contains("result = [lib.jar.txt, slf4j-api-1.7.25.jar.txt]")
        output.count('Single step transform received dependencies files [] for processing slf4j-api-1.7.24.jar') == 1
        output.count('Single step transform received dependencies files [] for processing slf4j-api-1.7.25.jar') == 1
        output.count('Single step transform received dependencies files [slf4j-api-1.7.24.jar] for processing lib.jar') == 1
        output.count('Single step transform received dependencies files [slf4j-api-1.7.25.jar] for processing lib.jar') == 1

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 0
        output.contains("result = [lib.jar.txt, slf4j-api-1.7.24.jar.txt]")
        output.contains("result = [lib.jar.txt, slf4j-api-1.7.25.jar.txt]")
    }

    @Issue("https://github.com/gradle/gradle/issues/15536")
    def "transform of external dependency with different upstream dependencies in multiple dependency graphs in the same project are executed"() {
        given:
        def lib1 = withColorVariants(mavenHttpRepo.module("test", "lib1", "1.2")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("test", "lib1", "1.3")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("test", "lib2", "5.6"))
            .dependsOn(lib1)
            .publish()
            .allowAll()

        setupBuildWithMultipleGraphsPerProject()
        setupSingleStepTransform()

        buildFile << """
            project(':app') {
                dependencies {
                    implementation 'test:lib2:5.6'
                    testImplementation 'test:lib1:1.3'
                }
            }
        """

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 4
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.2.jar.txt]")
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.3.jar.txt]")
        output.count('Single step transform received dependencies files [] for processing lib1-1.2.jar') == 1
        output.count('Single step transform received dependencies files [] for processing lib1-1.3.jar') == 1
        output.count('Single step transform received dependencies files [lib1-1.2.jar] for processing lib2-5.6.jar') == 1
        output.count('Single step transform received dependencies files [lib1-1.3.jar] for processing lib2-5.6.jar') == 1

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 0
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.2.jar.txt]")
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.3.jar.txt]")
    }

    def "transform does not receive artifacts for dependencies referenced only via a constraint"() {
        setupBuildWithSingleStep()
        buildFile("""
            project(":common") {
                dependencies {
                    constraints {
                        implementation project(":lib")
                        implementation 'unknown:unknown:1.3'
                    }
                }
            }
            project(":lib") {
                dependencies {
                    constraints {
                        implementation project(":common")
                    }
                }
            }
        """)

        when:
        run ":app:resolve"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar', 'common.jar', 'slf4j-api-1.7.25.jar'),
        )
    }

    @NotYetImplemented
    def "transform dependencies include multiple artifacts for the same output"() {
        setupBuildWithSingleStep()
        buildFile("""
            project(":common") {
                task secondProducer(type: FileProducer) {
                    output = layout.buildDirectory.file("common2.jar")
                    content = "common2"
                }
                artifacts {
                    implementation secondProducer.output
                }
            }
        """)

        when:
        run ":app:resolve"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar', 'common2.jar'), // Requested behavior: transforming common includes common2 as a dependency
            singleStep('common2.jar', 'common.jar'), // Requested behavior: transforming common2 includes common as a dependency
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar', 'common2.jar'),
        )
    }

    def "reuses result of transform of external dependency with different upstream dependencies when transform does not consume upstream dependencies"() {
        given:
        def lib1 = withColorVariants(mavenHttpRepo.module("test", "lib1", "1.2")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("test", "lib1", "1.3")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("test", "lib2", "5.6"))
            .dependsOn(lib1)
            .publish()
            .allowAll()

        setupBuildWithMultipleGraphsPerProject()
        setupTransformWithNoDependencies()

        buildFile << """
            project(':app') {
                dependencies {
                    implementation 'test:lib2:5.6'
                    testImplementation 'test:lib1:1.3'
                }
            }
        """

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 3
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.2.jar.txt]")
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.3.jar.txt]")
        output.count('Transforming without dependencies lib2-5.6.jar to lib2-5.6.jar.txt') == 1
        output.count('Transforming without dependencies lib1-1.2.jar to lib1-1.2.jar.txt') == 1
        output.count('Transforming without dependencies lib1-1.3.jar to lib1-1.3.jar.txt') == 1

        when:
        run ":app:resolve", ":app:resolveTest"

        then:
        output.count('Transforming') == 0
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.2.jar.txt]")
        output.contains("result = [lib2-5.6.jar.txt, lib1-1.3.jar.txt]")
    }

    @ToBeFixedForConfigurationCache(because = "treating file collection visit failures as a configuration cache problem adds an additional failure to the build summary")
    def "transform does not execute when dependencies cannot be found"() {
        given:
        mavenHttpRepo.module("unknown", "not-found", "4.3").allowAll().assertNotPublished()
        setupBuildWithTwoSteps()
        buildFile << """
            project(':lib') {
                dependencies {
                    implementation "unknown:not-found:4.3"
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        assertTransformationsExecuted()
        failure.assertHasDescription("Execution failed for task ':app:resolveGreen'") // failure is reported for task that takes the files as input
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Could not find unknown:not-found:4.3"))
    }

    @ToBeFixedForConfigurationCache(because = "treating file collection visit failures as a configuration cache problem adds an additional failure to the build summary")
    def "transform does not execute when dependencies cannot be downloaded"() {
        given:
        def cantBeDownloaded = withColorVariants(mavenHttpRepo.module("test", "cant-be-downloaded", "4.3")).publish()
        cantBeDownloaded.moduleMetadata.allowGetOrHead()
        cantBeDownloaded.artifact.expectDownloadBroken()
        setupBuildWithTwoSteps()

        buildFile << """
            project(':lib') {
                dependencies {
                    implementation "test:cant-be-downloaded:4.3"
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        failure.assertHasDescription("Execution failed for task ':app:resolveGreen'") // failure is reported for task that takes the files as input
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Could not download cant-be-downloaded-4.3.jar (test:cant-be-downloaded:4.3)"))

        assertTransformationsExecuted(
            transformStep1('common.jar'),
            transformStep1('slf4j-api-1.7.25.jar'),
            transformStep1('hamcrest-core-1.3.jar'),
            transformStep1('junit-4.11.jar': ['hamcrest-core-1.3.jar']),
            transformStep2('common.jar'),
            transformStep2('slf4j-api-1.7.25.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('junit-4.11.jar': ['hamcrest-core-1.3.jar'])
        )
    }

    def "transform does not execute when dependencies cannot be transformed"() {
        given:
        setupBuildWithFirstStepThatDoesNotUseDependencies()

        when:
        fails ":app:resolveGreen", '-DfailTransformOf=slf4j-api-1.7.25.jar'

        then:
        failure.assertHasDescription("Execution failed for task ':app:resolveGreen'") // failure is reported for task that takes the files as input
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Failed to transform slf4j-api-1.7.25.jar (org.slf4j:slf4j-api:1.7.25)"))

        assertTransformationsExecuted(
            simpleTransform('common.jar'),
            transformStep2('common.jar'),
            simpleTransform('hamcrest-core-1.3.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            simpleTransform('junit-4.11.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),

            simpleTransform('slf4j-api-1.7.25.jar'),
            simpleTransform('lib.jar'),
        )
    }

    def "transform does not execute when dependencies cannot be built"() {
        given:
        setupBuildWithTwoSteps()
        buildFile << """
            project(':common') {
                producer.doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        assertTransformationsExecuted()
        failure.assertHasDescription("Execution failed for task ':common:producer'")
        failure.assertHasFailures(1)
        failure.assertHasCause("broken")
    }

    Transformation simpleTransform(String artifact) {
        return new Transformation("SimpleTransform", artifact, [])
    }

    Transformation transformStep1(String artifact, String... dependencies) {
        return transformStep1((artifact): (dependencies as List))
    }

    Transformation transformStep1(Map<String, List<String>> artifactWithDependencies) {
        return Transformation.fromMap("Transform step 1", artifactWithDependencies)
    }

    Transformation singleStep(String artifact, String... dependencies) {
        return singleStep((artifact): (dependencies as List))
    }

    Transformation singleStep(Map<String, List<String>> artifactWithDependencies) {
        Transformation.fromMap("Single step transform", artifactWithDependencies)
    }

    Transformation transformStep2(String artifact, String... dependencies) {
        return transformStep2((artifact): (dependencies as List))
    }

    Transformation transformStep2(Map<String, List<String>> artifactWithDependencies) {
        return Transformation.fromMap("Transform step 2", artifactWithDependencies.collectEntries { artifact, dependencies -> [(artifact + ".txt"): dependencies.collect { it + ".txt" }] })
    }

    void assertTransformationsExecuted(Transformation... expectedTransforms) {
        assertTransformationsExecuted(ImmutableSortedMultiset.<Transformation> copyOf(expectedTransforms as List))
    }

    void assertTransformationsExecuted(Multiset<Transformation> expectedTransforms) {
        def transforms = executedTransformations()
        assert ImmutableSortedMultiset.copyOf(transforms) == expectedTransforms
    }

    List<Transformation> executedTransformations() {
        def withDependenciesPattern = Pattern.compile(/(.*) received dependencies files \[(.*)] for processing (.*)/)
        def simpleTransformPattern = Pattern.compile(/Transforming without dependencies (.*) to (.*)/)
        output.readLines().collect {
            def withDependenciesMatcher = withDependenciesPattern.matcher(it)
            if (withDependenciesMatcher.matches()) {
                return new Transformation(withDependenciesMatcher.group(1), withDependenciesMatcher.group(3), withDependenciesMatcher.group(2).empty ? [] : withDependenciesMatcher.group(2).split(", ").toList())
            }
            def simpleTransformMatcher = simpleTransformPattern.matcher(it)
            if (simpleTransformMatcher.matches()) {
                return new Transformation("SimpleTransform", simpleTransformMatcher.group(1), [])
            }
            return null
        }.findAll()
    }

    @Canonical
    static class Transformation implements Comparable<Transformation> {

        static Transformation fromMap(String name, Map<String, List<String>> artifactWithDependencies) {
            Iterables.getOnlyElement(artifactWithDependencies.entrySet()).with { entry -> new Transformation(name, entry.key, entry.value) }
        }

        Transformation(String name, String artifact, List<String> dependencies) {
            this.name = name
            this.artifact = artifact
            this.dependencies = dependencies
        }

        final String name
        final String artifact
        final List<String> dependencies

        @Override
        String toString() {
            "${name} - ${artifact} (${dependencies})"
        }

        @Override
        int compareTo(@Nonnull Transformation o) {
            name <=> o.name ?: artifact <=> o.artifact ?: Comparators.lexicographical(Comparator.<String> naturalOrder()).compare(dependencies, o.dependencies)
        }
    }
}
