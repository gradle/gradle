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

package org.gradle.integtests.composite

import org.gradle.test.fixtures.file.TestFile

class CompositeBuildArtifactTransformIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "can apply a transform to the outputs of included builds"() {
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        includedBuilds << buildB
        includedBuilds << buildC

        buildA.buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class XForm implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    File outputFile = outputs.file(input.name + ".xform")
                    def outputDirectory = outputFile.parentFile
                    println("Transforming \$input in output directory \$outputDirectory")
                    java.nio.file.Files.copy(input.toPath(), outputFile.toPath())
                }
            }

            dependencies {
                implementation 'org.test:buildB:1.2'
                implementation 'org.test:buildC:1.2'

                registerTransform(XForm) {
                    from.attribute(Attribute.of("artifactType", String), "jar")
                    to.attribute(Attribute.of("artifactType", String), "xform")
                }
            }

            task resolve {
                def artifacts = configurations.compileClasspath.incoming.artifactView {
                    attributes.attribute(Attribute.of("artifactType", String), "xform")
                }.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    artifacts.each {
                        println "Transformed artifact: \$it, location: \${it.file.absolutePath}"
                    }
                }
            }
        """
        expect:
        execute(buildA, "resolve")
        assertTaskExecuted(":buildB", ":jar")
        assertTaskExecuted(":buildC", ":jar")

        outputContains("Transformed artifact: buildB-1.0.jar.xform (project :buildB), location: ${expectedWorkspaceLocation(buildB)}")
        outputContains("Transformed artifact: buildC-1.0.jar.xform (project :buildC), location: ${expectedWorkspaceLocation(buildC)}")
        output.count("Transforming") == 2
    }

    private String expectedWorkspaceLocation(TestFile includedBuild) {
        includedBuild.file("build/.transforms")
    }
}
