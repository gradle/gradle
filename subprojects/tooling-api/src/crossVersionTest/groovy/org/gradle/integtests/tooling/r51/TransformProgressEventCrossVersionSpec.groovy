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

package org.gradle.integtests.tooling.r51


import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.util.GradleVersion

@ToolingApiVersion('>=5.1')
@TargetGradleVersion('>=5.4')
class TransformProgressEventCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    def setup() {
        settingsFile << """
            include 'lib', 'app'
        """
        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            def artifactType = Attribute.of('artifactType', String)
            subprojects {
                apply plugin: 'java'
            }
            project(":app") {
                dependencies {
                    implementation project(":lib")
                }
            }
        """
    }

    def "reports successful transform progress events"() {
        given:
        withFileSizerTransform()

        when:
        runBuild("resolve")

        then:
        def transformOperation = events.operation(applyTransform("FileSizer"))
        with(transformOperation) {
            assertIsTransform()
            descriptor.transformer.displayName == "FileSizer"
            descriptor.subject.displayName == transformTarget()
            successful
        }
        with(events.operation("Task :app:resolve")) {
            assertIsTask()
            successful
            descriptor.dependencies == [transformOperation.descriptor] as Set
        }
    }

    def "reports failed transform progress events"() {
        given:
        buildFile << """
            ${getBrokenTransform('intentional failure')}
            project(":app") {
                ${registerTransform('BrokenTransform')}
                ${createResolveTask()}
            }
        """

        when:
        runBuild("resolve")

        then:
        thrown(BuildException)
        with(events.operation(applyTransform("BrokenTransform"))) {
            assertIsTransform()
            descriptor.transformer.displayName == "BrokenTransform"
            descriptor.subject.displayName == transformTarget()
            !successful
            failures.size() == 1
            failures[0].description.contains("intentional failure")
        }
    }

    def "does not report transform progress events when TRANSFORM operations are not requested"() {
        given:
        withFileSizerTransform()

        when:
        runBuild("resolve", EnumSet.complementOf(EnumSet.of(OperationType.TRANSFORM)))

        then:
        events.operations.findAll { it.transform }.empty
    }

    def "reports dependencies of transform operations"() {
        given:
        buildFile << """
            $fileSizer
            $fileNamer
            project(":app") {
                ${registerTransform('FileSizer', 'jar', 'size')}
                ${registerTransform('FileNamer', 'size', 'name')}
                ${createResolveTask('name')}
            }
        """

        when:
        runBuild("resolve")

        then:
        def taskOperation = events.operation("Task :lib:jar")
        def firstTransformOperation = events.operation(applyTransform("FileSizer"))
        with(firstTransformOperation) {
            assertIsTransform()
            descriptor.transformer.displayName == "FileSizer"
            descriptor.subject.displayName == transformTarget()
            descriptor.dependencies == [taskOperation.descriptor] as Set
            successful
        }
        def secondTransformOperation = events.operation(applyTransform("FileNamer"))
        with(secondTransformOperation) {
            assertIsTransform()
            descriptor.transformer.displayName == "FileNamer"
            descriptor.subject.displayName == transformTarget()
            descriptor.dependencies == [firstTransformOperation.descriptor] as Set
            successful
        }
    }

    def "reports transform progress events for included builds"() {
        given:
        withFileSizerTransform()
        settingsFile.moveToDirectory(file('included'))
        buildFile.moveToDirectory(file('included'))
        settingsFile << """
            includeBuild 'included'
        """
        buildFile << """
            task run {
                dependsOn gradle.includedBuild('included').task(':app:resolve')
            }
        """

        when:
        runBuild("run")

        then:
        def taskOperation = events.operation("Task :included:lib:jar")
        def transformOperation = events.operation(applyTransform("FileSizer", ":included:lib"))
        with(transformOperation) {
            assertIsTransform()
            descriptor.transformer.displayName == "FileSizer"
            descriptor.subject.displayName == transformTarget(":included:lib")
            descriptor.dependencies == [taskOperation.descriptor] as Set
            successful
        }
        with(events.operation("Task :included:app:resolve")) {
            assertIsTask()
            successful
            descriptor.dependencies == [transformOperation.descriptor] as Set
        }
    }

    private TestFile withFileSizerTransform() {
        buildFile << """
            $fileSizer
            project(":app") {
                ${registerTransform('FileSizer')}
                ${createResolveTask()}
            }
        """
    }

    private static String getFileSizer() {
        """
            abstract class FileSizer implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    output.text = String.valueOf(input.length())
                }
            }
        """
    }

    def getFileNamer() {
        """
            abstract class FileNamer implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    output.text = String.valueOf(input.name)
                }
            }
        """
    }

    def getBrokenTransform(String message) {
        """
            abstract class BrokenTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    throw new GradleException("$message")
                }
            }
        """
    }

    def registerTransform(String transformImplementation, String from = "jar", String to = "size") {
        """
            dependencies {
                registerTransform($transformImplementation) {
                    from.attribute(artifactType, '$from')
                    to.attribute(artifactType, '$to')
                }
            }
        """
    }

    def createResolveTask(String attribute = "size") {
        """
            task resolve(type: Copy) {
                from configurations.runtimeClasspath.incoming.artifactView {
                    attributes { it.attribute(artifactType, '$attribute') }
                }.artifacts.artifactFiles
                into "\$buildDir/libs"
            }
        """
    }

    String applyTransform(String transformName, String project = ":lib") {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "Transform lib.jar (project $project) with $transformName"
        } else {
            return "Transform artifact lib.jar (project $project) with $transformName"
        }
    }

    String transformTarget(String project = ":lib") {
        if (targetVersion.baseVersion >= GradleVersion.version("6.6")) {
            return "lib.jar (project $project)"
        } else {
            return "artifact lib.jar (project $project)"
        }
    }

    private Object runBuild(String task, Set<OperationType> operationTypes = EnumSet.allOf(OperationType)) {
        withConnection {
            newBuild()
                .forTasks(task)
                .addProgressListener(events, operationTypes)
                .run()
        }
    }

}
