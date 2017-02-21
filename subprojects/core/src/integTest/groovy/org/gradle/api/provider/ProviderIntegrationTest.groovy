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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseFileSeparators

class ProviderIntegrationTest extends AbstractIntegrationSpec {

    private static final String OUTPUT_FILE_CONTENT = 'Hello World!'
    File defaultOutputFile
    File customOutputFile

    def setup() {
        defaultOutputFile = file('build/output.txt')
        customOutputFile = file('build/custom.txt')
    }

    @Unroll
    def "can create and use provider by custom task written as #language class"() {
        given:
        file("buildSrc/src/main/$language/MyTask.${language}") << taskTypeImpl
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()

        when:
        buildFile << """
             myTask {
                enabled = provider(true)
                outputFiles = provider(files("${normaliseFileSeparators(customOutputFile.canonicalPath)}"))
            }
        """
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
        customOutputFile.isFile()
        customOutputFile.text == OUTPUT_FILE_CONTENT

        where:
        language | taskTypeImpl
        'groovy' | customGroovyBasedTaskType()
        'java'   | customJavaBasedTaskType()
    }

    def "can assign default values for provider properties"() {
        given:
        buildFile << """
            task myTask(type: MyTask)

            class MyTask extends DefaultTask {
                private Provider<Boolean> enabled = project.defaultProvider(Boolean)
                private Provider<FileCollection> outputFiles = project.defaultProvider(FileCollection)
                
                @Input
                boolean getEnabled() {
                    enabled.get()
                }
                
                void setEnabled(Provider<Boolean> enabled) {
                    this.enabled = enabled
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles.get()
                }

                void setOutputFiles(Provider<FileCollection> outputFiles) {
                    this.outputFiles = outputFiles
                }
                
                @TaskAction
                void resolveValue() {
                    if (getEnabled()) {
                        getOutputFiles().each {
                            it.text = '$OUTPUT_FILE_CONTENT'
                        }
                    }
                }
            }
        """

        when:
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
    }

    @Unroll
    def "can lazily map extension property value to task property with #mechanism"() {
        given:
        buildFile << pluginWithExtensionMapping { taskConfiguration }
        buildFile << customGroovyBasedTaskType()

        when:
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
        customOutputFile.isFile()
        customOutputFile.text == OUTPUT_FILE_CONTENT

        where:
        mechanism            | taskConfiguration
        'convention mapping' | taskConfiguredWithConventionMapping()
        'provider'           | taskConfiguredWithProvider()
    }

    def "can use provider to define a task dependency"() {
        given:
        buildFile << """
            task producer {
                ext.destFile = file('test.txt')
                outputs.file destFile

                doLast {
                    destFile << '$OUTPUT_FILE_CONTENT'
                }
            }

            def targetFile = provider { producer.destFile }
            
            targetFile.builtBy producer

            task consumer {
                dependsOn targetFile

                doLast {
                    println targetFile.get().text
                }
            }
        """

        when:
        succeeds('consumer')

        then:
        executedTasks.containsAll(':producer', ':consumer')
        outputContains(OUTPUT_FILE_CONTENT)
    }

    static String customGroovyBasedTaskType() {
        """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.provider.Provider
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFiles

            class MyTask extends DefaultTask {
                private Provider<Boolean> enabled = project.provider(false)
                private Provider<FileCollection> outputFiles = project.provider(project.files())
                
                @Input
                boolean getEnabled() {
                    enabled.get()
                }
                
                void setEnabled(Provider<Boolean> enabled) {
                    this.enabled = enabled
                }
                
                void setEnabled(Boolean enabled) {
                    this.enabled = project.provider(enabled)
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles.get()
                }

                void setOutputFiles(Provider<FileCollection> outputFiles) {
                    this.outputFiles = outputFiles
                }
                
                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles = project.provider(outputFiles)
                }

                @TaskAction
                void resolveValue() {
                    if (getEnabled()) {
                        getOutputFiles().each {
                            it.text = '$OUTPUT_FILE_CONTENT'
                        }
                    }
                }
            }
        """
    }

    static String customJavaBasedTaskType() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.OutputFiles;

            import java.io.BufferedWriter;
            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;
            import java.util.concurrent.Callable;
        
            public class MyTask extends DefaultTask {
                private Provider<Boolean> enabled;
                private Provider<FileCollection> outputFiles;

                public MyTask() {
                    enabled = getProject().provider(false);
                    outputFiles = getProject().provider(getProject().files());
                }

                @Input
                public boolean getEnabled() {
                    return enabled.get();
                }
                
                public void setEnabled(Provider<Boolean> enabled) {
                    this.enabled = enabled;
                }
                
                @OutputFiles
                public FileCollection getOutputFiles() {
                    return outputFiles.get();
                }

                public void setOutputFiles(Provider<FileCollection> outputFiles) {
                    this.outputFiles = outputFiles;
                }
                
                @TaskAction
                public void resolveValue() throws IOException {
                    if (getEnabled()) {
                        for (File outputFile : getOutputFiles()) {
                            writeFile(outputFile, "$OUTPUT_FILE_CONTENT");
                        }
                    }
                }
                
                private void writeFile(File destination, String content) throws IOException {
                    BufferedWriter output = null;
                    try {
                        output = new BufferedWriter(new FileWriter(destination));
                        output.write(content);
                    } finally {
                        if (output != null) {
                            output.close();
                        }
                    }
                }
            }
        """
    }

    private String pluginWithExtensionMapping(Closure taskCreation) {
        """
            apply plugin: MyPlugin
            
            pluginConfig {
                enabled = true
                outputFiles = files('${normaliseFileSeparators(customOutputFile.canonicalPath)}')
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def extension = project.extensions.create('pluginConfig', MyExtension)

                    ${taskCreation()}
                }
            }

            class MyExtension {
                Boolean enabled
                FileCollection outputFiles
            }
        """
    }

    static String taskConfiguredWithConventionMapping() {
        """
            project.tasks.create('myTask', MyTask) {
                conventionMapping.enabled = { extension.enabled }
                conventionMapping.outputFiles = { extension.outputFiles }
            }
        """
    }

    static String taskConfiguredWithProvider() {
        """
            project.tasks.create('myTask', MyTask) {
                enabled = project.provider { extension.enabled }
                outputFiles = project.provider { extension.outputFiles }
            }
        """
    }
}
