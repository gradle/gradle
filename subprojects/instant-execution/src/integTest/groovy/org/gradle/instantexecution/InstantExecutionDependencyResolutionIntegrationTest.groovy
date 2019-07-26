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
    def "task input files can include artifact transform output"() {
        setupBuildWithColorTransformAction()
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            dependencies {
                implementation project(':a')
                implementation project(':b')
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()
                
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        expect:
        instantRun(":resolve")
        outputContains("result = [a.jar.green, b.jar.green]")
        instantRun(":resolve")
        // For now, transforms are ignored when writing to the cache
        outputContains("result = []")
    }
}
