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

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.test.fixtures.server.http.BlockingHttpServer

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class TransformLoggingIntegrationTest extends AbstractConsoleGroupedTaskFunctionalTest {
    ConsoleOutput consoleType

    private static final List<ConsoleOutput> TESTED_CONSOLE_TYPES = [ConsoleOutput.Plain, ConsoleOutput.Verbose, ConsoleOutput.Rich, ConsoleOutput.Auto]

    def setup() {
        createDirs("lib", "util", "app")
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'util'
            include 'app'
        """

        buildFile << """
            import java.nio.file.Files

            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                    registerTransform(GreenMultiplier) {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "green")
                    }
                    registerTransform(BlueMultiplier) {
                        from.attribute(artifactType, "green")
                        to.attribute(artifactType, "blue")
                    }
                }
                configurations {
                    compile {
                        attributes.attribute usage, 'api'
                    }
                }
                ["blue", "green"].each { type ->
                    tasks.register("resolve\${type.capitalize()}") {
                        def artifacts = configurations.compile.incoming.artifactView {
                            attributes { it.attribute(artifactType, type) }
                        }.artifacts

                        inputs.files artifacts.artifactFiles

                        doLast {
                            println "files: " + artifacts.collect { it.file.name }
                        }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    archiveFileName = 'lib2.jar'
                }
                tasks.withType(Jar) {
                    destinationDirectory = buildDir
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }

            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }

            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class Multiplier implements TransformAction<TransformParameters.None> {
                private final boolean showOutput = System.getProperty("showOutput") != null
                private final String target

                Multiplier(String target) {
                    if (showOutput) {
                        println("Creating multiplier")
                    }
                    this.target = target
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Override
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output1 = outputs.file(input.name + ".1." + target)
                    def output2 = outputs.file(input.name + ".2." + target)
                    if (showOutput) {
                        println("Transforming \${input.name} to \${input.name}.\${target}")
                    }
                    Files.copy(input.toPath(), output1.toPath())
                    Files.copy(input.toPath(), output2.toPath())
                }
            }

            abstract class GreenMultiplier extends Multiplier {
                GreenMultiplier() {
                    super("green")
                }
            }
            abstract class BlueMultiplier extends Multiplier {
                BlueMultiplier() {
                    super("blue")
                }
            }
        """
    }

    def "does not show transformation headers when there is no output for #type console"() {
        consoleType = type

        when:
        succeeds(":util:resolveGreen")
        then:
        result.groupedOutput.transformCount == 0

        where:
        type << TESTED_CONSOLE_TYPES
    }

    def "does show transformation headers when there is output for #type console"() {
        consoleType = type

        when:
        succeeds(":util:resolveGreen", "-DshowOutput")
        then:
        result.groupedOutput.transformCount == 2

        result.groupedOutput.transform("GreenMultiplier", "lib1.jar (project :lib)")
            .assertOutputContains("Creating multiplier")
            .assertOutputContains("Transforming lib1.jar to lib1.jar.green")

        result.groupedOutput.transform("GreenMultiplier", "lib2.jar (project :lib)")
            .assertOutputContains("Creating multiplier")
            .assertOutputContains("Transforming lib2.jar to lib2.jar.green")

        where:
        type << TESTED_CONSOLE_TYPES
    }

    def "progress display name is 'Transforming' for top level transforms"() {
        // Build scan plugin filters artifact transform logging by the name of the progress display name
        // since that is the only way it currently can distinguish transforms.
        // When it has a better way, then this test can be removed.
        consoleType = ConsoleOutput.Rich
        BlockingHttpServer server = new BlockingHttpServer()
        server.start()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(Red) {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "red")
                    }
                }
                tasks.register("resolveRed") {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'red') }
                    }.artifacts

                    inputs.files artifacts.artifactFiles

                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                    }
                }
            }

            // NOTE: This is named "Red" so that the status message fits on one line
            abstract class Red extends Multiplier {
                Red() {
                    super("red")
                }

                @Override
                void transform(TransformOutputs outputs) {
                    ${server.callFromBuildUsingExpression("inputArtifact.get().asFile.name")}
                    super.transform(outputs)
                }
            }
        """

        when:
        def block = server.expectConcurrentAndBlock("lib1.jar", "lib2.jar")
        def build = executer.withTasks(":util:resolveRed").start()
        then:
        block.waitForAllPendingCalls()
        poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> Transforming lib1.jar (project :lib) with Red > Red lib1.jar")
            RichConsoleStyling.assertHasWorkInProgress(build, "> Transforming lib2.jar (project :lib) with Red > Red lib2.jar")
        }

        block.releaseAll()
        build.waitForFinish()

        cleanup:
        server.stop()
    }

    def "each step is logged separately"() {
        consoleType = ConsoleOutput.Plain

        when:
        succeeds(":util:resolveBlue", "-DshowOutput")
        then:
        result.groupedOutput.transformCount == 4
        def initialSubjects = ["lib1.jar (project :lib)", "lib2.jar (project :lib)"] as Set
        result.groupedOutput.subjectsFor('GreenMultiplier') == initialSubjects
        result.groupedOutput.subjectsFor('BlueMultiplier') == initialSubjects
    }
}
