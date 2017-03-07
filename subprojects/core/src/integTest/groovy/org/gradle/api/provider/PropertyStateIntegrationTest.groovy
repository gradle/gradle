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

class PropertyStateIntegrationTest extends AbstractIntegrationSpec {

    private static final String OUTPUT_FILE_CONTENT = 'Hello World!'
    File defaultOutputFile
    File customOutputFile

    def setup() {
        defaultOutputFile = file('build/output.txt')
        customOutputFile = file('build/custom.txt')
    }

    @Unroll
    def "can create and use property state by custom task written as #language class"() {
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
                enabled = true
                outputFiles = files("${normaliseFileSeparators(customOutputFile.canonicalPath)}")
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

    @Unroll
    def "can lazily map extension property state to task property with #mechanism"() {
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
        'property state'     | taskConfiguredWithPropertyState()
    }

    def "can use property state to define a task dependency"() {
        given:
        buildFile << """
            task producer {
                ext.destFile = file('test.txt')
                outputs.file destFile

                doLast {
                    destFile << '$OUTPUT_FILE_CONTENT'
                }
            }

            def targetFile = property(File)
            targetFile.set(producer.destFile)
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

    def "can use property state type to infer task dependency"() {
        given:
        buildFile << """
            task producer(type: Producer) {
                text = '$OUTPUT_FILE_CONTENT'
                outputFiles = files("\$buildDir/helloWorld.txt")
            }

            task consumer(type: Consumer) {
                inputFiles = producer.outputs.files
            }

            class Producer extends DefaultTask {
                @Input
                String text

                private final PropertyState<FileCollection> outputFiles = project.property(FileCollection)
                
                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.set(outputFiles)
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles.get()
                }

                @TaskAction
                void produce() {
                    getOutputFiles().each {
                        it << text
                    }
                }
            }
            
            class Consumer extends DefaultTask {
                @InputFiles
                FileCollection inputFiles
                
                @TaskAction
                void consume() {
                    inputFiles.each {
                        println it.text
                    }
                }
            }
        """

        when:
        succeeds('consumer')

        then:
        executedTasks.containsAll(':producer', ':consumer')
        outputContains(OUTPUT_FILE_CONTENT)
    }

    def "can inject and use property state factory"() {
        file("buildSrc/src/main/java/MyTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.PropertyStateFactory;
            import org.gradle.api.tasks.TaskAction;
            
            import javax.inject.Inject;

            public class MyTask extends DefaultTask {
                @Inject
                public MyTask(PropertyStateFactory propertyStateFactory) {
                    propertyStateFactory.property(String.class);
                }
                
                @Inject
                public PropertyStateFactory getPropertyStateFactory() {
                    throw new UnsupportedOperationException();
                }

                @TaskAction
                public void doSomething() {
                    getPropertyStateFactory().property(String.class);
                }
            }
        """
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        noExceptionThrown()
    }

    static String customGroovyBasedTaskType() {
        """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.provider.PropertyState
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFiles

            class MyTask extends DefaultTask {
                private PropertyState<Boolean> enabled = project.property(Boolean)
                private PropertyState<FileCollection> outputFiles = project.property(FileCollection)
                
                MyTask() {
                    outputFiles.set(project.files())
                }

                @Input
                boolean getEnabled() {
                    enabled.get()
                }
                
                void setEnabled(PropertyState<Boolean> enabled) {
                    this.enabled = enabled
                }
                
                void setEnabled(boolean enabled) {
                    this.enabled.set(enabled)
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles.get()
                }

                void setOutputFiles(PropertyState<FileCollection> outputFiles) {
                    this.outputFiles = outputFiles
                }
                
                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.set(outputFiles)
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
            import org.gradle.api.file.ConfigurableFileCollection;
            import org.gradle.api.provider.PropertyState;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.OutputFiles;

            import java.io.BufferedWriter;
            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;
            import java.util.concurrent.Callable;
        
            public class MyTask extends DefaultTask {
                private PropertyState<Boolean> enabled;
                private PropertyState<ConfigurableFileCollection> outputFiles;

                public MyTask() {
                    enabled = getProject().property(Boolean.class);
                    outputFiles = getProject().property(ConfigurableFileCollection.class);
                    outputFiles.set(getProject().files());
                }

                @Input
                public boolean getEnabled() {
                    return enabled.get();
                }
                
                public void setEnabled(PropertyState<Boolean> enabled) {
                    this.enabled = enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled.set(enabled);
                }

                @OutputFiles
                public ConfigurableFileCollection getOutputFiles() {
                    return outputFiles.get();
                }

                public void setOutputFiles(PropertyState<ConfigurableFileCollection> outputFiles) {
                    this.outputFiles = outputFiles;
                }

                public void setOutputFiles(ConfigurableFileCollection outputFiles) {
                    this.outputFiles.set(outputFiles);
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
                    def extension = project.extensions.create('pluginConfig', MyExtension, project)

                    ${taskCreation()}
                }
            }

            class MyExtension {
                private final PropertyState<Boolean> enabled
                private final PropertyState<FileCollection> outputFiles

                MyExtension(Project project) {
                    enabled = project.property(Boolean)
                    outputFiles = project.property(FileCollection)
                }

                PropertyState<Boolean> getEnabled() {
                    enabled
                }

                void setEnabled(Boolean enabled) {
                    this.enabled.set(enabled)
                }

                PropertyState<FileCollection> getOutputFiles() {
                    outputFiles
                }

                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.set(outputFiles)
                }
            }
        """
    }

    static String taskConfiguredWithConventionMapping() {
        """
            project.tasks.create('myTask', MyTask) {
                conventionMapping.enabled = { extension.enabled.get() }
                conventionMapping.outputFiles = { extension.outputFiles.get() }
            }
        """
    }

    static String taskConfiguredWithPropertyState() {
        """
            project.tasks.create('myTask', MyTask) {
                enabled = extension.enabled
                outputFiles = extension.outputFiles
            }
        """
    }
}
