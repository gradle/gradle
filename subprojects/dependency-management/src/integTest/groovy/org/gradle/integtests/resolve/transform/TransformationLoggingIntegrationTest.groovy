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

class TransformationLoggingIntegrationTest extends AbstractConsoleGroupedTaskFunctionalTest {
    ConsoleOutput consoleType

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'util'
            include 'app'
        """

        buildFile << """
            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)
                
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }                    
                }
                configurations {
                    compile {
                        attributes.attribute usage, 'api'
                    }
                }
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts

                    inputs.files size.artifactFiles
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

            class FileSizer extends ArtifactTransform {
                private boolean showOutput = System.getProperty("showOutput") != null
            
                FileSizer() {
                    if (showOutput) {
                        println "Creating FileSizer"
                    }
                }
                
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".txt")
                    if (showOutput) {
                        println "Transforming \${input.name} to \${output.name}"
                    }
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }
        """
    }

//    @Unroll
    def "does not show transformation headers when there is no output for #type console"() {
        consoleType = ConsoleOutput.Plain

        when:
        succeeds(":util:resolve")
        then:
        result.groupedOutput.transformationCount == 0

        where:
        type << ConsoleOutput.values()
    }

    @Unroll
    def "does show transformation headers when there is output for #type console"() {
        consoleType = type

        when:
        succeeds(":util:resolve", "-DshowOutput")
        then:
        result.groupedOutput.transformationCount == 2

        where:
        type << [ConsoleOutput.Plain, ConsoleOutput.Rich, ConsoleOutput.Verbose]
    }
}
