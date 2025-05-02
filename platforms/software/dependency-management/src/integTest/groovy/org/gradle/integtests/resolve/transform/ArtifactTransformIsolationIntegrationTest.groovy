/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

/**
 * Ensures that artifact transform parameters are isolated from one another and the surrounding project state.
 */
class ArtifactTransformIsolationIntegrationTest extends AbstractHttpDependencyResolutionTest implements ArtifactTransformTestFixture {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
def artifactType = Attribute.of('artifactType', String)

class Counter implements Serializable {
    private int count = 0;

    public int increment() {
        return ++count;
    }

    public int getCount() {
        return count;
    }
}

class Resolve extends Copy {

    @Internal
    ArtifactCollection artifacts
    @Internal
    final artifactType = Attribute.of('artifactType', String)
    private final ConfigurableFileCollection artifactFiles

    @Inject
    Resolve(ObjectFactory objectFactory) {
        artifactFiles = objectFactory.fileCollection()
        from(artifactFiles)
        doLast {
            println "files: " + artifacts.collect { it.file.name }
            println "ids: " + artifacts.collect { it.id }
            println "components: " + artifacts.collect { it.id.componentIdentifier }
            println "variants: " + artifacts.collect { it.variant.attributes }
        }
    }

    void setArtifactTypeAttribute(String artifactTypeAttribute) {
        artifacts = project.configurations.compile.incoming.artifactView {
            attributes { it.attribute(artifactType, artifactTypeAttribute) }
        }.artifacts
        artifactFiles.setFrom(artifacts.artifactFiles)
    }
}
"""
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "serialized mutable class is isolated during artifact transformation"() {
        mavenRepo.module("test", "test", "1.3").publish()
        mavenRepo.module("test", "test2", "2.3").publish()

        given:
        buildFile << """
            abstract class CountRecorder implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters{
                    @Input
                    Counter getCounter()
                    void setCounter(Counter counter)
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                CountRecorder() {
                    println "Creating CountRecorder"
                }

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    def counter = parameters.counter
                    println "Transforming \${input.name} to \${output.name}"
                    output.withWriter { out ->
                        out.println String.valueOf(counter.getCount())
                        for (int i = 0; i < 4; i++) {
                            out.println String.valueOf(counter.increment())
                        }
                        out.close()
                    }
                }
            }

            def buildScriptCounter = new Counter()

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                compile
            }

            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }

            dependencies {
                registerTransform(CountRecorder) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'firstCount')
                    parameters {
                        counter = buildScriptCounter
                    }
                }
                buildScriptCounter.increment() // should not be captured during registration
                registerTransform(CountRecorder) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'secondCount')
                    parameters {
                        counter = buildScriptCounter
                    }
                }
                registerTransform(CountRecorder) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'thirdCount')
                    parameters {
                        counter = buildScriptCounter
                    }
                }
            }

            tasks.withType(Resolve).configureEach {
                doLast {
                    buildScriptCounter.increment()
                }
            }

            task resolveFirst(type: Resolve) {
                artifactTypeAttribute = 'firstCount'
                into "\${buildDir}/libs1"
            }

            task resolveSecond(type: Resolve) {
                artifactTypeAttribute = 'secondCount'
                into "\${buildDir}/libs2"
            }

            task resolveThird(type: Resolve) {
                artifactTypeAttribute = 'thirdCount'
                into "\${buildDir}/libs3"
            }

            task resolve dependsOn 'resolveFirst', 'resolveSecond', 'resolveThird'
        """

        when:
        run 'resolve', '--max-workers=1'

        then:
        outputContains("variants: [{artifactType=firstCount, org.gradle.status=release}, {artifactType=firstCount, org.gradle.status=release}]")
        file("build/libs1").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs1/test-1.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
        file("build/libs1/test2-2.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]

        and:
        outputContains("variants: [{artifactType=secondCount, org.gradle.status=release}, {artifactType=secondCount, org.gradle.status=release}]")
        file("build/libs2").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        if (GradleContextualExecuter.configCache) {
            // Counter is serialized and isolated prior to execution, so transforms will not see the increment in each tasks' doLast { } (which is good)
            file("build/libs1/test-1.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
            file("build/libs1/test2-2.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
        } else {
            // Counter is isolated at execution time, so transforms will see the increment in each tasks' doLast { }
            file("build/libs2/test-1.3.jar.txt").readLines() == ["2", "3", "4", "5", "6"]
            file("build/libs2/test2-2.3.jar.txt").readLines() == ["2", "3", "4", "5", "6"]
        }

        and:
        outputContains("variants: [{artifactType=thirdCount, org.gradle.status=release}, {artifactType=thirdCount, org.gradle.status=release}]")
        file("build/libs3").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        if (GradleContextualExecuter.configCache) {
            // Counter is serialized and isolated prior to execution, so transforms will not see the increment in each tasks' doLast { } (which is good)
            file("build/libs1/test-1.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
            file("build/libs1/test2-2.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
        } else {
            // Counter is isolated at execution time, so transforms will see the increment in each tasks' doLast { }
            file("build/libs3/test-1.3.jar.txt").readLines() == ["3", "4", "5", "6", "7"]
            file("build/libs3/test2-2.3.jar.txt").readLines() == ["3", "4", "5", "6", "7"]
        }

        and:
        if (GradleContextualExecuter.configCache) {
            // Counter is serialized and isolated prior to execution, so transforms will not see the increment in each tasks' doLast { } (which is good)
            output.count("Transforming") == 2
            output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1
            output.count("Transforming test2-2.3.jar to test2-2.3.jar.txt") == 1
        } else {
            // Counter is isolated at execution time, so transforms will see the increment in each tasks' doLast { }
            output.count("Transforming") == 6
            output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 3
            output.count("Transforming test2-2.3.jar to test2-2.3.jar.txt") == 3
        }
    }

    def "cannot register a transform from a custom classloader"() {
        createDirs("producer", "consumer")
        settingsFile << """
            include 'producer', 'consumer'
        """
        buildFile << """
            def classLoader = new GroovyClassLoader(this.class.classLoader)
            def MakeGreen = classLoader.parseClass(\"\"\"abstract class MakeGreen implements ${TransformAction.name}<${TransformParameters.name}.None> {
                @${InputArtifact.name}
                abstract ${Provider.name}<${FileSystemLocation.name}> getInputArtifact()

                void transform(${TransformOutputs.name} outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }\"\"\")"""
        setupBuildWithColorTransform(buildFile)

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """
        def isConfigCache = GradleContextualExecuter.configCache

        when:
        fails ':consumer:resolve'
        then:
        failureDescriptionContains(isConfigCache ? "MakeGreen" : "Execution failed for task ':consumer:resolve'.")
        failureCauseContains(isConfigCache ? "MakeGreen" : "Could not isolate parameters null of artifact transform MakeGreen")
    }
}
