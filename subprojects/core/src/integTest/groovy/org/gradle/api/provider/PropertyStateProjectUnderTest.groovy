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

import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.TextUtil.normaliseFileSeparators

class PropertyStateProjectUnderTest {

    private static final String OUTPUT_FILE_CONTENT = 'Hello World!'
    private final TestFile projectDir
    private final TestFile buildFile
    private final TestFile defaultOutputFile
    private final TestFile customOutputFile

    PropertyStateProjectUnderTest(TestFile projectDir) {
        this.projectDir = projectDir
        buildFile = file('build.gradle')
        defaultOutputFile = file('build/output.txt')
        customOutputFile = file('build/custom.txt')
    }

    private TestFile file(Object... path) {
        projectDir.file(path)
    }

    TestFile getCustomOutputFile() {
        customOutputFile
    }

    void writeCustomTaskTypeToBuildSrc(Language language) {
        switch (language) {
            case Language.GROOVY: writeCustomGroovyBasedTaskTypeToBuildSrc()
                                  break
            case Language.JAVA: writeCustomJavaBasedTaskTypeToBuildSrc()
                                break
            default: throw IllegalArgumentException("Unsupported language for testing $language")
        }
    }

    static enum Language {
        GROOVY('Java'), JAVA('Java')

        private final String description

        Language(String description) {
            this.description = description
        }

        @Override
        String toString() {
            description
        }
    }

    String writeCustomGroovyBasedTaskTypeToBuildSrc() {
        file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.file.ConfigurableFileCollection
            import org.gradle.api.provider.PropertyState
            import org.gradle.api.provider.Provider
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFiles

            class MyTask extends DefaultTask {
                private final PropertyState<Boolean> enabled = project.property(Boolean)
                private final ConfigurableFileCollection outputFiles = project.files()

                @Input
                boolean getEnabled() {
                    enabled.get()
                }
                
                void setEnabled(Provider<Boolean> enabled) {
                    this.enabled.set(enabled)
                }
                
                void setEnabled(boolean enabled) {
                    this.enabled.set(enabled)
                }
                
                @OutputFiles
                FileCollection getOutputFiles() {
                    outputFiles
                }
                
                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.setFrom(outputFiles)
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

    void writeCustomJavaBasedTaskTypeToBuildSrc() {
        file("buildSrc/src/main/java/MyTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.file.ConfigurableFileCollection;
            import org.gradle.api.provider.PropertyState;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.OutputFiles;

            import java.io.BufferedWriter;
            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;
        
            public class MyTask extends DefaultTask {
                private final PropertyState<Boolean> enabled;
                private final ConfigurableFileCollection outputFiles;

                public MyTask() {
                    enabled = getProject().property(Boolean.class);
                    outputFiles = getProject().files();
                }

                @Input
                public boolean getEnabled() {
                    return enabled.get();
                }
                
                public void setEnabled(Provider<Boolean> enabled) {
                    this.enabled.set(enabled);
                }

                public void setEnabled(boolean enabled) {
                    this.enabled.set(enabled);
                }

                @OutputFiles
                public FileCollection getOutputFiles() {
                    return outputFiles;
                }

                public void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.setFrom(outputFiles);
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

    void writePluginWithExtensionMappingUsingConventionMapping() {
        writePluginWithExtensionMapping(taskConfiguredWithConventionMapping())
    }

    void writePluginWithExtensionMappingUsingPropertyState() {
        writePluginWithExtensionMapping(taskConfiguredWithPropertyState())
    }

    private void writePluginWithExtensionMapping(String taskCreation) {
        buildFile << """
            apply plugin: MyPlugin
            
            pluginConfig {
                enabled = true
                outputFiles = files('${normaliseFileSeparators(customOutputFile.canonicalPath)}')
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def extension = project.extensions.create('pluginConfig', MyExtension, project)

                    $taskCreation
                }
            }

            class MyExtension {
                private final PropertyState<Boolean> enabled
                private final ConfigurableFileCollection outputFiles

                MyExtension(Project project) {
                    enabled = project.property(Boolean)
                    outputFiles = project.files()
                }

                Boolean getEnabled() {
                    enabled.get()
                }

                Provider<Boolean> getEnabledProvider() {
                    enabled
                }

                void setEnabled(Boolean enabled) {
                    this.enabled.set(enabled)
                }

                FileCollection getOutputFiles() {
                    outputFiles
                }

                void setOutputFiles(FileCollection outputFiles) {
                    this.outputFiles.setFrom(outputFiles)
                }
            }
        """
    }

    private String taskConfiguredWithConventionMapping() {
        """
            project.tasks.create('myTask', MyTask) {
                conventionMapping.enabled = { extension.enabled }
                conventionMapping.outputFiles = { extension.outputFiles }
            }
        """
    }

    private String taskConfiguredWithPropertyState() {
        """
            project.tasks.create('myTask', MyTask) {
                enabled = extension.enabledProvider
                outputFiles = extension.outputFiles
            }
        """
    }

    void assertDefaultOutputFileDoesNotExist() {
        assert !defaultOutputFile.exists()
    }

    void assertCustomOutputFileContent() {
        assert customOutputFile.isFile()
        assert customOutputFile.text == OUTPUT_FILE_CONTENT
    }
}
