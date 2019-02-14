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

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.options.OptionValues
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Unroll

import static org.gradle.util.Matchers.matchesRegexp

class ArtifactTransformValuesInjectionIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

    def "transform can receive parameters, workspace and input artifact via abstract getter"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            extension = 'green'
                        }
                    }
                }
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                String getExtension()
                void setExtension(String value)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                @InputArtifact
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + "." + parameters.extension)
                    output.text = "ok"
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform parameters are validated for input output annotations"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                String getExtension()
                void setExtension(String value)
                @OutputDirectory
                File getOutputDir()
                void setOutputDir(File outputDir)
                @Input
                String getMissingInput()
                void setMissingInput(String missing)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                
                void transform(ArtifactTransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        failure.assertThatDescription(matchesRegexp('Cannot isolate parameters MakeGreen\\$Inject@.* of artifact transform MakeGreenAction'))
        failure.assertHasCause('Some problems were found with the configuration of the artifact transform parameter MakeGreen.')
        failure.assertHasCause("Property 'extension' is not annotated with an input annotation.")
        failure.assertHasCause("Property 'outputDir' is annotated with unsupported annotation @OutputDirectory.")
        failure.assertHasCause("Property 'missingInput' does not have a value specified.")
    }

    @Unroll
    def "transform parameters type cannot use annotation @#annotation.simpleName"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                String getExtension()
                void setExtension(String value)
                @${annotation.simpleName}
                String getBad()
                void setBad(String value)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                
                void transform(ArtifactTransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        failure.assertThatDescription(matchesRegexp('Cannot isolate parameters MakeGreen\\$Inject@.* of artifact transform MakeGreenAction'))
        failure.assertHasCause('A problem was found with the configuration of the artifact transform parameter MakeGreen.')
        failure.assertHasCause("Property 'bad' is annotated with unsupported annotation @${annotation.simpleName}.")

        where:
        annotation << [OutputFile, OutputFiles, OutputDirectory, OutputDirectories, Destroys, LocalState, OptionValues]
    }

    @Unroll
    def "transform parameters type cannot use injection annotation @#annotation.simpleName"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                String getExtension()
                void setExtension(String value)
                @${annotation.simpleName}
                String getBad()
                void setBad(String value)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                
                void transform(ArtifactTransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('Could not create an instance of type MakeGreen.')
        failure.assertHasCause('Could not generate a decorated class for interface MakeGreen.')
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method MakeGreen.getBad().")

        where:
        annotation << [InputArtifact, InputArtifactDependencies, TransformParameters]
    }

    def "transform action is validated for input output annotations"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                String getExtension()
                void setExtension(String value)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                
                @InputFile
                File inputFile 
                
                File notAnnotated 

                @InputFile @InputArtifact @InputArtifactDependencies
                File getConflictingAnnotations() { } 
                
                void transform(ArtifactTransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('Some problems were found with the configuration of MakeGreenAction.')
        failure.assertHasCause("Property 'conflictingAnnotations' is annotated with unsupported annotation @InputFile.")
        failure.assertHasCause("Property 'conflictingAnnotations' has conflicting property types declared: @InputArtifact, @InputArtifactDependencies.")
        failure.assertHasCause("Property 'inputFile' is annotated with unsupported annotation @InputFile.")
        failure.assertHasCause("Property 'notAnnotated' is not annotated with an input annotation.")
    }

    @Unroll
    def "transform action type cannot use annotation @#annotation.simpleName"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                String getExtension()
                void setExtension(String value)
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @${annotation.simpleName}
                String getBad() { }
                
                void transform(ArtifactTransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('A problem was found with the configuration of MakeGreenAction.')
        failure.assertHasCause("Property 'bad' is annotated with unsupported annotation @${annotation.simpleName}.")

        where:
        annotation << [Input, InputFile, InputDirectory, OutputFile, OutputFiles, OutputDirectory, OutputDirectories, Destroys, LocalState, OptionValues, Console, Internal]
    }

    @Unroll
    def "transform can receive dependencies via abstract getter of type #targetType"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransformAction()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}
project(':b') {
    dependencies {
        implementation project(':c')
    }
}

abstract class MakeGreen implements TransformAction {
    @InputArtifactDependencies
    abstract ${targetType} getDependencies()
    @InputArtifact
    abstract File getInput()
    
    void transform(ArtifactTransformOutputs outputs) {
        println "received dependencies files \${dependencies*.name} for processing \${input.name}"
        def output = outputs.file(input.name + ".green")
        output.text = "ok"
    }
}

"""

        when:
        run(":a:resolve")

        then:
        outputContains("received dependencies files [] for processing c.jar")
        outputContains("received dependencies files [c.jar] for processing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        targetType << ["FileCollection", "Iterable<File>"]
    }

    @Unroll
    def "old style transform cannot use @#annotation.name"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
allprojects {
    dependencies {
        registerTransform {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
            artifactTransform(MakeGreen)
        }
    }
}

project(':a') {
    dependencies {
        implementation project(':b')
        implementation project(':c')
    }
}

abstract class MakeGreen extends ArtifactTransform {
    @${annotation.name}
    abstract File getInputFile()
    
    List<File> transform(File input) {
        println "processing \${input.name}"
        def output = new File(outputDirectory, input.name + ".green")
        output.text = "ok"
        return [output]
    }
}

"""

        when:
        fails(":a:resolve")

        then:
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method MakeGreen.getInputFile().")

        where:
        annotation << [InputArtifact, InputArtifactDependencies, TransformParameters]
    }

    def "transform cannot receive parameter object via constructor parameter"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                String getExtension()
                void setExtension(String value)
            }
            
            class MakeGreenAction implements TransformAction {
                private MakeGreen conf
                
                @Inject
                MakeGreenAction(MakeGreen conf) {
                    this.conf = conf
                }
                
                void transform(ArtifactTransformOutputs outputs) {
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreenAction: ${file('b/build/b.jar')}.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of interface MakeGreen, or no service of type interface MakeGreen")
    }

    @Unroll
    def "transform cannot use @InputArtifact to receive dependencies"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransformAction()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}

abstract class MakeGreen implements TransformAction {
    @InputArtifact
    abstract FileCollection getDependencies()
    
    void transform(ArtifactTransformOutputs outputs) {
        dependencies.files
        throw new RuntimeException("broken")
    }
}
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type interface ${FileCollection.name} available.")
    }

    def "transform cannot use @Inject to receive input file"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransformAction()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}

abstract class MakeGreen implements TransformAction {
    @Inject
    abstract File getWorkspace()
    
    void transform(ArtifactTransformOutputs outputs) {
        workspace
        throw new RuntimeException("broken")
    }
}
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type class ${File.name} available.")
    }

    @Unroll
    def "task implementation cannot use injection annotation @#annotation.simpleName"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @${annotation.name}
                File getThing() { null }
            }

            tasks.create('broken', MyTask)
        """

        expect:
        fails('broken')
        failure.assertHasCause("Could not create task of type 'MyTask'.")
        failure.assertHasCause("Could not generate a decorated class for class MyTask.")
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method MyTask.getThing().")

        where:
        annotation << [InputArtifact, InputArtifactDependencies, TransformParameters]
    }
}
