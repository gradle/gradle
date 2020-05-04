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

package org.gradle.instantexecution.inputs.undeclared

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.test.fixtures.file.TestFile

trait JavaPluginImplementation {
    void javaPlugin(TestFile sourceFile) {
        sourceFile << """
            import ${Action.name};
            import ${Project.name};
            import ${Plugin.name};
            import ${Task.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    String value = System.getProperty("GET_PROPERTY");
                    System.out.println("apply GET_PROPERTY = " + value);

                    value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default");
                    System.out.println("apply GET_PROPERTY_OR_DEFAULT = " + value);

                    // Inside a lambda body
                    lambda("apply").run();

                    project.getTasks().register("thing", t -> {
                        t.doLast(new Action<Task>() {
                            public void execute(Task t) {
                                String value = System.getProperty("GET_PROPERTY");
                                System.out.println("task GET_PROPERTY = " + value);

                                value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default");
                                System.out.println("task GET_PROPERTY_OR_DEFAULT = " + value);

                                lambda("task").run();
                            }
                        });
                    });
                }

                static Runnable lambda(String location) {
                    return () -> {
                        System.out.println(location + " LAMBDA = " + System.getProperty("LAMBDA"));
                    };
                }
            }
        """
    }
}
