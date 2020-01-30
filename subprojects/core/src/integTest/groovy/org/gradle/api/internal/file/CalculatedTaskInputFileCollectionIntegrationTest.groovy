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

package org.gradle.api.internal.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CalculatedTaskInputFileCollectionIntegrationTest extends AbstractIntegrationSpec {

    def "dependencies of the inputs are propagated to the calculated file collection"() {
        buildFile << """
            import org.gradle.api.internal.file.collections.MinimalFileSet
            import org.gradle.api.internal.file.TaskFileVarFactory
            import org.gradle.api.internal.tasks.TaskDependencyContainer
            import org.gradle.api.internal.tasks.TaskDependencyResolveContext
            import javax.inject.Inject

            class MyFileSet implements MinimalFileSet, TaskDependencyContainer {
                private dependency

                MyFileSet(dependency) {
                    this.dependency = dependency
                }

                @Override
                String getDisplayName() {
                    "File Set"
                }

                @Override
                Set<File> getFiles() {
                    [] as Set
                }

                @Override
                void visitDependencies(TaskDependencyResolveContext context) {
                    context.add(dependency.get())
                }
            }

            abstract class MyTask extends DefaultTask {
                @Internal
                abstract ConfigurableFileCollection getInputFiles()

                @InputFiles
                final FileCollection calculatedInputFiles

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @Internal
                abstract Property<Object> getDependency()

                @Inject
                MyTask(TaskFileVarFactory fileVarFactory) {
                    this.calculatedInputFiles = fileVarFactory.newCalculatedInputFileCollection(
                        this,
                        new MyFileSet(dependency),
                        inputFiles
                    )
                }

                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = "done"
                }
            }

            task producer {
                outputs.file("build/my-output.txt")
                doLast {
                    file("build/my-output.txt").text = "Produced"
                }
            }

            task fileSetDependency {
                doLast {
                    println "Hello"
                }
            }

            task myTask(type: MyTask) {
                outputFile = file("build/output.txt")
                inputFiles.from(producer.outputs)
                dependency = fileSetDependency
            }
        """

        when:
        succeeds "myTask"
        then:
        executedAndNotSkipped(":fileSetDependency", ":producer", ":myTask")
    }

}
