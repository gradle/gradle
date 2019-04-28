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

package org.gradle.api.tasks

import org.gradle.test.fixtures.file.TestFile

/**
 * Methods that define Groovy implementations for certain task input and output patterns.
 */
trait TasksWithInputsAndOutputs {
    abstract TestFile getBuildFile()

    def taskTypeWithOutputFileProperty() {
        buildFile << """
            class FileProducer extends DefaultTask {
                @OutputFile
                final RegularFileProperty output = project.objects.fileProperty()
                @Input
                String content = "content" // set to empty string to delete file
            
                @TaskAction
                def go() {
                    def file = output.get().asFile
                    if (content.empty) {
                        file.delete()
                    } else {
                        file.text = content
                    }
                }
            }
        """
    }

    def taskTypeWithOutputDirectoryProperty() {
        buildFile << """
            class DirProducer extends DefaultTask {
                @OutputDirectory
                final DirectoryProperty output = project.objects.directoryProperty()
                @Input
                final ListProperty<String> names = project.objects.listProperty(String)
                @Input
                String content = "content" // set to empty string to delete directory
            
                @TaskAction
                def go() {
                    def dir = output.get().asFile
                    if (content.empty) {
                        project.delete(dir)
                    } else {
                        project.delete(dir)
                        dir.mkdirs()
                        names.get().forEach {
                            new File(dir, it).text = content
                        }
                    }
                }
            }
        """
    }

    def taskTypeWithMultipleOutputFiles() {
        buildFile << """
            // Not using properties
            class OutputFilesTask extends DefaultTask {
                @OutputFile
                File out1
                @OutputFile
                File out2
                @TaskAction
                def go() {
                    out1.text = "1"
                    out2.text = "2"
                }
            }
        """
    }

    def taskTypeWithMultipleOutputFileProperties() {
        buildFile << """
            class OutputFilesTask extends DefaultTask {
                @OutputFile
                final RegularFileProperty out1 = project.objects.fileProperty()
                @OutputFile
                final RegularFileProperty out2 = project.objects.fileProperty()
                @TaskAction
                def go() {
                    out1.get().asFile.text = "1"
                    out2.get().asFile.text = "2"
                }
            }
        """
    }

    def taskTypeWithInputProperty() {
        buildFile << """
            class InputTask extends DefaultTask {
                @Input
                final Property<Integer> inValue = project.objects.property(Integer)
                @OutputFile
                final RegularFileProperty outFile = project.objects.fileProperty()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = (inValue.get() + 10) as String
                }
            }
        """
    }

    def taskTypeWithInputFileProperty() {
        buildFile << """
            class InputFileTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inFile = project.objects.fileProperty()
                @OutputFile
                final RegularFileProperty outFile = project.objects.fileProperty()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = inFile.get().asFile.text
                }
            }
        """
    }

    def taskTypeWithInputFilesProperty() {
        buildFile << """
            class InputFilesTask extends DefaultTask {
                @InputFiles
                final inFiles = project.files()
                @OutputFile
                final RegularFileProperty outFile = project.objects.fileProperty()
                @TaskAction
                def go() {
                    outFile.get().asFile.text = inFiles*.text.sort().join(',')
                }
            }
        """
    }
}
