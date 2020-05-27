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

package org.gradle.instantexecution

import org.gradle.util.GradleVersion

class InstantExecutionArtifactTransformIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "can handle input file collection with task produced artifacts"() {
        given:
        buildFile << '''
            import org.gradle.api.artifacts.*
            import org.gradle.api.artifacts.transform.*
            import java.nio.file.Files

            abstract class MyTask extends DefaultTask {
                @OutputFile
                public abstract RegularFileProperty getOutputFile();

                @Input
                public abstract Property<Integer> getInputCount();

                @TaskAction
                public void doTask() {
                    File outputFile = getOutputFile().get().asFile
                    outputFile.delete()
                    outputFile.parentFile.mkdirs()
                    Files.write(outputFile.toPath(), ("Count is: " + getInputCount().get()).getBytes())
                }
            }

            abstract class ConsumerTask extends DefaultTask {
                @Internal
                public ArtifactCollection artifactCollection;

                @InputFiles
                public FileCollection getMyInputFiles() {
                    return artifactCollection.artifactFiles
                }

                @OutputFile
                public abstract RegularFileProperty getOutputFile();

                @TaskAction
                public void doTask() {
                    File outputFile = getOutputFile().get().asFile
                    outputFile.delete()
                    outputFile.parentFile.mkdirs()
                    String outputContent = "";
                    for(File f: getMyInputFiles().files) {
                        outputContent += f.canonicalPath + "\\n"
                    }
                    Files.write(outputFile.toPath(), outputContent.getBytes())
                }
            }

            abstract class MyTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                public abstract Provider<FileSystemLocation> getPrimaryInput();

                @Override
                void transform(TransformOutputs transformOutputs) {
                    File file = getPrimaryInput().get().asFile;
                    println "Processing $file. File exists = ${file.exists()}"
                    if (file.exists()) {
                        File outputFile = transformOutputs.file("copy");
                        Files.copy(file.toPath(), outputFile.toPath())
                    } else {
                        throw new RuntimeException("File does not exist: " + file.canonicalPath);
                    }
                }
            }

            def artifactType = Attribute.of('artifactType', String)
            dependencies.registerTransform(MyTransform.class) {
                it.from.attribute(artifactType, "jar")
                it.to.attribute(artifactType, "my-custom-type")
            }

            TaskProvider<MyTask> myTaskProvider = tasks.register("myTask", MyTask.class) {
                it.getInputCount().set(10)
                it.getOutputFile().set(new File("build/myTask/output/file.jar"))
            }

            Configuration includedConfiguration = configurations.create("includedConfiguration")

            ConfigurableFileCollection combinedInputs = project.files(includedConfiguration, myTaskProvider.map { it.outputFile })
            Configuration myConfiguration = configurations.create("myConfiguration")
            dependencies.add(myConfiguration.name, combinedInputs)

            tasks.register("consumerTask", ConsumerTask.class) {
                it.artifactCollection = myConfiguration.incoming.artifactView { ArtifactView.ViewConfiguration viewConfiguration ->
                    viewConfiguration.attributes.attribute(artifactType, "my-custom-type")
                }.artifacts
                it.outputFile.set(new File("build/consumerTask/output/output.txt"))
            }
        '''
        def instant = newInstantExecutionFixture()
        def outputFile = file('build/consumerTask/output/output.txt')
        def expectWarning = {
            executer.expectDeprecationWarning(
                "Type 'ConsumerTask': field 'artifactCollection' without corresponding getter has been " +
                    "annotated with @Internal. This behaviour has been deprecated and is scheduled to be " +
                    "removed in Gradle 7.0. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks" +
                    " for more details."
            )
        }

        when:
        expectWarning()
        instantRun 'consumerTask'
        def originalOutput = outputFile.text

        then:
        instant.assertStateStored()

        when:
        expectWarning()
        instantRun 'consumerTask'

        then:
        instant.assertStateLoaded()
        outputFile.text == originalOutput
    }
}
