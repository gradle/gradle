/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.util.internal.ToBeImplemented

class ArtifactTransformContinuousBuildIntegrationTest extends AbstractContinuousIntegrationTest implements ArtifactTransformTestFixture {

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    @ToBeImplemented("We treat parameters as an opaque hash")
    def "changes to artifact transform parameters trigger a build"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()

        file("buildSrc/src/main/groovy/MakeGreen.groovy") << """
            import java.io.File
            import javax.inject.Inject;
            import org.gradle.api.provider.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*
            import org.gradle.api.artifacts.transform.*
            import org.gradle.work.*

            abstract class MakeGreen implements TransformAction<Parameters> {

                interface Parameters extends TransformParameters {
                    @InputFiles
                    ConfigurableFileCollection getInputFiles()
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    def inputFile = input.get().asFile
                    println "Transforming " + inputFile.name
                    def outputFile = outputs.file(inputFile.name + ".green")
                    outputFile.text = "green"
                }
            }
        """

        buildFile << """
            allprojects { p ->
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            inputFiles.from(project.file("input/input.txt"))
                        }
                    }
                }
            }
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        def producerInputFile = file("producer/input/input.txt")
        producerInputFile.text = "input"

        when:
        run ":consumer:resolve"
        then:
        executedAndNotSkipped(":consumer:resolve")

        when:
        producerInputFile.text = "changed"
        then:
        // TODO: A build should be triggered, though it isn't since currently combine the secondary inputs into one hash
        //   buildTriggeredAndSucceeded()
        noBuildTriggered()
    }
}
