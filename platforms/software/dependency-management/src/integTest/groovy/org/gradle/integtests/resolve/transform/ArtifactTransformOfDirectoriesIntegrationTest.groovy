/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class ArtifactTransformOfDirectoriesIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    @Issue("https://github.com/gradle/gradle/issues/15351")
    def "can transform a file dependency that contains a directory"() {
        taskTypeWithOutputDirectoryProperty()
        taskTypeLogsInputFileCollectionContent()
        transformDirectoryDependency()

        when:
        run("resolve")

        then:
        result.assertTaskExecuted(":producer")
        output.count("transforming [dir1]") == 1
        outputContains("result = [dir1.size]")

        when:
        run("resolve")

        then:
        result.assertTaskExecuted(":producer")
        output.count("transforming") == 0
        outputContains("result = [dir1.size]")
    }

    @Issue("https://github.com/gradle/gradle/issues/15351")
    def "can transform a file dependency that contains a missing directory"() {
        taskTypeWithOutputDirectoryProperty()
        taskTypeLogsInputFileCollectionContent()
        transformDirectoryDependency()
        buildFile << """
            producer.content = "" // generate missing directory
        """

        when:
        run("resolve")

        then:
        result.assertTaskExecuted(":producer")
        output.count("transforming") == 0
        outputContains("result = []")

        when:
        run("resolve")

        then:
        result.assertTaskExecuted(":producer")
        output.count("transforming") == 0
        outputContains("result = []")
    }

    def transformDirectoryDependency() {
        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters.None

            def artifactType = Attribute.of('artifactType', String)

            task producer(type: DirProducer) {
                output = project.layout.projectDirectory.dir('dir1')
            }
            configurations {
                compile
            }
            dependencies {
                compile files(producer.output)

                registerTransform(MakeSize) {
                    from.attribute(artifactType, 'directory')
                    to.attribute(artifactType, 'size')
                }
            }

            def transformed = configurations.compile.incoming.artifactView { attributes.attribute(artifactType, 'size') }.files

            task resolve(type: ShowFilesTask) {
                inFiles.from(transformed)
            }

            abstract class MakeSize implements TransformAction<None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    def dir = input.get().asFile
                    assert dir.directory
                    println("transforming [\${dir.name}]")
                    outputs.file("\${dir.name}.size").text = 'ok'
                }
            }
        """
    }
}
