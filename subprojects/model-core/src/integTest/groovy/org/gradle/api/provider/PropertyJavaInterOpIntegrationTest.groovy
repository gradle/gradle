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

package org.gradle.api.provider

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import javax.inject.Inject

// Require Java 8+ to verify that Java lambdas work with the APIs.
@Requires(TestPrecondition.JDK8_OR_LATER)
class PropertyJavaInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        pluginDir.file("build.gradle") << """
            plugins { 
                id("java-library")
            }
            dependencies {
                api gradleApi()
            }
        """
        pluginDir.file("src/main/java/SomeTask.java") << """
            import ${DefaultTask.name};
            import ${Property.name};
            import ${ObjectFactory.name};
            import ${TaskAction.name};
            import ${Inject.name};

            public class SomeTask extends DefaultTask {
                private final Property<Boolean> flag;
                private final Property<String> message;
                
                @Inject
                public SomeTask(ObjectFactory objectFactory) {
                    flag = objectFactory.property(Boolean.class);
                    message = objectFactory.property(String.class);
                }
                
                public Property<Boolean> getFlag() {
                    return flag;
                }

                public Property<String> getMessage() {
                    return message;
                }
                
                @TaskAction
                public void run() {
                    System.out.println("flag = " + flag.get());
                    System.out.println("message = " + message.get());
                }
            }
        """
    }

    @Override
    void pluginSetsValues() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class, t -> {
                        t.getFlag().set(true);
                        t.getMessage().set("some value");
                    });
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValues() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class, t -> {
                        t.getFlag().set(project.provider(() -> true));
                        t.getMessage().set(project.provider(() -> "some value"));
                    });
                }
            }
        """
    }

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class);
                }
            }
        """
    }
}
