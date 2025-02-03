/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class CompositeBuildSpeedIntegrationTest extends AbstractIntegrationSpec {

    BuildOperationsFixture buildOperationsFixture = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "without"() {
        buildFile << """
            class PrintTask extends DefaultTask {
                @TaskAction
                void print() {
                    println 'Hello'
                }
            }

            tasks.register("print", PrintTask)
        """

        println(testDirectory)

        expect:
        fails("print")
    }

    def "buildsrc java"() {
        file("buildSrc/src/main/java/PrintTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class PrintTask extends DefaultTask {
                @TaskAction
                void print() {
                    System.out.println("Hello");
                }
            }
        """

        buildFile << """
            tasks.register("print", PrintTask)
        """

        expect:
        succeeds("print")
    }

    def "buildsrc groovy"() {
        file("buildSrc/src/main/groovy/PrintTask.groovy") << """
             import org.gradle.api.DefaultTask;
             import org.gradle.api.tasks.TaskAction;

             class PrintTask extends DefaultTask {
                 @TaskAction
                 void print() {
                     System.out.println("Hello");
                 }
             }
        """

        buildFile << """
            tasks.register("print", PrintTask)
        """

        expect:
        succeeds("print")
    }

    def "included java"() {
        settingsFile << """
            includeBuild("other")
        """

        file("other/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }

            gradlePlugin {
                plugins {
                    greeting {
                        id = 'com.example.plugin'
                        implementationClass = 'com.example.plugin.PrintPlugin'
                    }
                }
            }
        """

        file("other/src/main/java/com/example/plugin/PrintPlugin.java") << """
            package com.example.plugin;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class PrintPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    // Do nothing.
                }
            }
        """

        file("other/src/main/java/com/example/plugin/PrintTask.java") << """
            package com.example.plugin;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class PrintTask extends DefaultTask {
                @TaskAction
                public void print() {
                    System.out.println("Hello");
                }
            }
        """

        buildFile << """
            plugins {
                id("com.example.plugin")
            }

            tasks.register("print", com.example.plugin.PrintTask)
        """

        expect:
        succeeds("print")
    }

    def "included two java"() {
        settingsFile << """
            includeBuild("other")
            includeBuild("other2")
            includeBuild("other3")
        """

        ["other", "other2", "other3"].each {
            file("${it}/build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                }

                gradlePlugin {
                    plugins {
                        greeting {
                            id = 'com.example.${it}'
                            implementationClass = 'com.example.plugin.${it}PrintPlugin'
                        }
                    }
                }
            """

            file("${it}/src/main/java/com/example/plugin/${it}PrintPlugin.java") << """
                package com.example.plugin;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class ${it}PrintPlugin implements Plugin<Project> {
                    @Override
                    public void apply(Project project) {
                        // Do nothing.
                    }
                }
            """

            file("${it}/src/main/java/com/example/plugin/${it}PrintTask.java") << """
                package com.example.plugin;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskAction;

                public class ${it}PrintTask extends DefaultTask {
                    @TaskAction
                    public void print() {
                        System.out.println("Hello");
                    }
                }
            """
        }

        buildFile << """
            plugins {
                id("com.example.other")
                id("com.example.other2")
                id("com.example.other3")
            }

            import com.example.plugin.otherPrintTask
            import com.example.plugin.other2PrintTask
            import com.example.plugin.other3PrintTask

            tasks.register("print", otherPrintTask)
            tasks.register("print2", other2PrintTask)
            tasks.register("print3", other3PrintTask)
        """

        println(testDirectory)

        expect:
        succeeds("print", "print2", "print3")
//        succeeds("print", "print2", "print3")
//        succeeds("print", "print2", "print3")
//        succeeds("print", "print2", "print3")
//        succeeds("print", "print2", "print3")
    }


    def "included groovy"() {
        settingsFile << """
            includeBuild("other")
        """

        file("other/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }

            gradlePlugin {
                plugins {
                    greeting {
                        id = 'com.example.plugin'
                        implementationClass = 'com.example.plugin.PrintPlugin'
                    }
                }
            }
        """

        file("other/src/main/groovy/com/example/plugin/PrintPlugin.groovy") << """
            package com.example.plugin;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class PrintPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    // Do nothing.
                }
            }
        """

        file("other/src/main/groovy/com/example/plugin/PrintTask.groovy") << """
            package com.example.plugin;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class PrintTask extends DefaultTask {
                @TaskAction
                public void print() {
                    System.out.println("Hello");
                }
            }
        """

        buildFile << """
            plugins {
                id("com.example.plugin")
            }

            tasks.register("print", com.example.plugin.PrintTask)
        """

        expect:
        succeeds("print")
    }


}
