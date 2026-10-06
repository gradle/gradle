/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

class CompositeBuildWorkGraphCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "reports cycle across builds from task to transform to task"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                ${transformClass()}
                ${producerAndConsumer()}
                ${registerTransform()}

                dependencies {
                    deps("org.test:buildC:1.0")
                }

                tasks.named("make") {
                    inputs.files(configurations.resolveDeps.incoming.artifactView {
                        attributes.attribute(Attribute.of("artifactType", String), "size")
                    }.files)
                }
            """
        }
        def buildC = singleProjectBuild("buildC") {
            buildFile << """
                ${producerAndConsumer()}

                dependencies {
                    deps("org.test:buildB:1.0")
                }

                tasks.named("make") {
                    inputs.files(configurations.resolveDeps)
                }
            """
        }
        includedBuilds << buildB
        includedBuilds << buildC

        buildA.buildFile.text = """
            tasks.register("run") {
                dependsOn(gradle.includedBuild("buildB").task(":make"))
            }
        """

        when:
        fails(buildA, "run")

        then:
        failure.assertHasDescription("""Circular dependency between the following tasks:
:buildB:make
\\--- Transform buildC.txt (project ':buildC') with FileSizer
     \\--- :buildC:make
          \\--- :buildB:make (*)""")
    }

    @ToBeFixedForIsolatedProjects(because = "Uses subprojects to share transform class between projects")
    def "reports cycle within a build that includes only transforms"() {
        given:
        def buildB = multiProjectBuild("buildB", ["lib", "consumer"]) {
            buildFile << """
                import org.gradle.api.artifacts.transform.TransformParameters

                abstract class TransformWithParams implements TransformAction<Parameters> {
                    interface Parameters extends TransformParameters {
                        @InputFiles
                        ConfigurableFileCollection getExtra()
                    }

                    @InputArtifact
                    abstract Provider<FileSystemLocation> getInputArtifact()

                    void transform(TransformOutputs outputs) {
                        def input = inputArtifact.get().asFile
                        outputs.file(input.name + ".xform").text = input.text
                    }
                }

                subprojects {
                    ${producerAndConsumer()}
                }

                project(":consumer") {
                    def artifactType = Attribute.of("artifactType", String)
                    def view = { String type ->
                        configurations.resolveDeps.incoming.artifactView {
                            attributes.attribute(artifactType, type)
                        }.files
                    }

                    dependencies {
                        deps(project(":lib"))

                        // Each transform consumes the output of the other transform as a parameter
                        registerTransform(TransformWithParams) {
                            from.attribute(artifactType, "txt")
                            to.attribute(artifactType, "one")
                            parameters.extra.from(view("two"))
                        }
                        registerTransform(TransformWithParams) {
                            from.attribute(artifactType, "txt")
                            to.attribute(artifactType, "two")
                            parameters.extra.from(view("one"))
                        }
                    }

                    tasks.named("make") {
                        inputs.files(view("one"))
                    }
                }
            """
        }
        includedBuilds << buildB

        buildA.buildFile.text = """
            tasks.register("run") {
                dependsOn(gradle.includedBuild("buildB").task(":consumer:make"))
            }
        """

        when:
        fails(buildA, "run")

        then:
        // A cycle of only transforms cannot span builds, as edges between builds always target a task.
        // Such a cycle is detected when the execution plan for the single build is determined, which
        // reports only the first node of the cycle and only follows edges to tasks.
        failure.assertHasDescription("""Circular dependency between the following tasks:
Transform lib.txt (project ':buildB:lib') with TransformWithParams""")
    }

    private static String producerAndConsumer() {
        """
            def usage = Attribute.of("org.test.usage", String)

            def make = tasks.register("make") {
                def outputFile = layout.buildDirectory.file("\${project.name}.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.text = project.name
                }
            }

            configurations {
                dependencyScope("deps")
                resolvable("resolveDeps") {
                    extendsFrom(deps)
                    attributes.attribute(usage, "text")
                }
                consumable("elements") {
                    attributes.attribute(usage, "text")
                    outgoing.artifact(make)
                }
            }
        """
    }

    private static String transformClass() {
        """
            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class FileSizer implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    File outputFile = outputs.file(input.name + ".txt")
                    outputFile.text = String.valueOf(input.length())
                }
            }
        """
    }

    private static String registerTransform() {
        """
            dependencies {
                registerTransform(FileSizer) {
                    from.attribute(Attribute.of("artifactType", String), "txt")
                    to.attribute(Attribute.of("artifactType", String), "size")
                }
            }
        """
    }
}
