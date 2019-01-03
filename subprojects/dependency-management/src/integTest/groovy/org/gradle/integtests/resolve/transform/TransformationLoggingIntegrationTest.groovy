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
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.FeaturePreviewsFixture.enableIncrementalArtifactTransformations

class TransformationLoggingIntegrationTest extends AbstractConsoleGroupedTaskFunctionalTest {
    ConsoleOutput consoleType

    private static final List<ConsoleOutput> TESTED_CONSOLE_TYPES = [ConsoleOutput.Plain, ConsoleOutput.Verbose, ConsoleOutput.Rich]

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'util'
            include 'app'
        """
        enableIncrementalArtifactTransformations(settingsFile)

        buildFile << """ 
            import java.nio.file.Files

            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)
                
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "green")
                        artifactTransform(GreenMultiplier)
                    }                    
                    registerTransform {
                        from.attribute(artifactType, "green")
                        to.attribute(artifactType, "blue")
                        artifactTransform(BlueMultiplier)
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
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    archiveName = 'lib2.jar'
                }
                tasks.withType(Jar) {
                    destinationDir = buildDir
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

            class Multiplier extends ArtifactTransform {
                private final String target
                private final boolean showOutput = System.getProperty("showOutput") != null
        
                @javax.inject.Inject
                Multiplier(String target) {
                    if (showOutput) {
                        println("Creating multiplier")
                    }
                    this.target = target
                }
        
                @Override
                List<File> transform(File input) {
                    def output1 = new File(outputDirectory, input.name + ".1." + target)
                    def output2 = new File(outputDirectory, input.name + ".2." + target)
                    if (showOutput) {
                        println("Transforming \${input.name} to \${input.name}.\${target}")
                    }
                    Files.copy(input.toPath(), output1.toPath())
                    Files.copy(input.toPath(), output2.toPath())
                    return [output1, output2]
                }
            }
            
            class GreenMultiplier extends Multiplier {
                GreenMultiplier() {
                    super("green")
                }
            }
            class BlueMultiplier extends Multiplier {
                BlueMultiplier() {
                    super("blue")
                }
            }
        """
    }

    @Unroll
    def "does not show transformation headers when there is no output for #type console"() {
        consoleType = type

        when:
        succeeds(":util:resolveGreen")
        then:
        result.groupedOutput.transformationCount == 0

        where:
        type << TESTED_CONSOLE_TYPES
    }

    @Unroll
    def "does show transformation headers when there is output for #type console"() {
        consoleType = type

        when:
        succeeds(":util:resolveGreen", "-DshowOutput")
        then:
        result.groupedOutput.transformationCount == 2

        where:
        type << TESTED_CONSOLE_TYPES
    }

    def "each step is logged separately"() {
        consoleType = ConsoleOutput.Plain

        when:
        succeeds(":util:resolveBlue", "-DshowOutput")
        then:
        result.groupedOutput.transformationCount == 4
        def initialSubjects = ((1..2).collect { "lib${it}.jar (project :lib)".toString() }) as Set
        result.groupedOutput.subjectsFor('GreenMultiplier') == initialSubjects
        result.groupedOutput.subjectsFor('BlueMultiplier') == initialSubjects
    }
}
