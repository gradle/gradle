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

package org.gradle.api.file


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.OutputFile

abstract class AbstractFilePropertyJavaInterOpIntegrationTest extends AbstractFilePropertyLanguageInterOpIntegrationTest {
    def setup() {
        pluginDir.file("build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                api gradleApi()
            }
        """
    }

    abstract void taskDefinition();

    abstract void taskWithNestedBeanDefinition();

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("producer", ProducerTask.class, t -> {
                        t.getOutFile().set(project.getLayout().getBuildDirectory().file("intermediate.txt"));
                    });
                }
            }
        """
        taskDefinition()
    }

    @Override
    void pluginDefinesTaskWithNestedBean() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("producer", ProducerTask.class, t -> {
                        t.getParams().getOutFile().set(project.getLayout().getBuildDirectory().file("intermediate.txt"));
                    });
                }
            }
        """
        pluginDir.file("src/main/java/Params.java") << """
            import ${RegularFileProperty.name};
            import ${OutputFile.name};

            public interface Params {
                @OutputFile
                RegularFileProperty getOutFile();
            }
        """
        taskWithNestedBeanDefinition()
    }
}
