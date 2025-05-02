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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest

class CompositeContinuousBuildIntegrationTest extends AbstractContinuousIntegrationTest {
    def setup() {
        buildTestFixture.withBuildInSubDir()
    }

    def "will rebuild on input change for included build task dependency"() {
        def outputFile = file("included/build/output.txt")
        def inputFile = file("included/inputs/input.txt")
        inputFile.text = "first"
        singleProjectBuild("included") {
            buildFile << """
                task someTask {
                    def inputFile = file("inputs/input.txt")
                    def outputFile = file("build/output.txt")
                    inputs.file inputFile
                    outputs.file outputFile
                    doLast {
                        outputFile.parentFile.mkdirs()
                        outputFile.text = inputFile.text
                    }
                }
            """
        }

        settingsFile << """
            rootProject.name = "root"
            includeBuild "included"
        """

        buildFile << """
            task composite {
                dependsOn gradle.includedBuild("included").task(":someTask")
            }
        """

        when:
        succeeds("composite")
        then:
        outputFile.text == "first"

        when:
        inputFile.text = "second"
        then:
        buildTriggeredAndSucceeded()
        outputFile.text == "second"
    }

    def "will rebuild on change for included build library dependency"() {
        def includedLibrary = singleProjectBuild("library") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def librarySource = includedLibrary.file("src/main/java/org/test/Library.java")
        librarySource << """
            package org.test;
            public class Library {
                public static void print(String who) {
                    System.out.println("Hello " + who);
                }
            }
        """

        settingsFile << """
            rootProject.name = "root"
            includeBuild "library"
        """
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'
            group = 'com.example'
            application {
                mainClass = 'com.example.Main'
            }
            dependencies {
                implementation 'org.test:library:0.1'
            }
        """
        def mainSource = file("src/main/java/com/example/Main.java")
        mainSource << """
            package com.example;

            public class Main {
                public static void main(String... args) {
                    org.test.Library.print("World");
                }
            }
        """
        when:
        succeeds("run")
        then:
        outputContains("Hello World")

        when:
        librarySource.text = librarySource.text.replace("Hello", "Goodbye")
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye World")

        when:
        mainSource.text = mainSource.text.replace("World", "Friend")
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye Friend")
    }

    def "will rebuild on change for plugin supplied by included build"() {
        // to reduce contention with concurrently executing tests
        requireOwnGradleUserHomeDir()
        executer.requireIsolatedDaemons()

        def includedLibrary = singleProjectBuild("plugin") {
            buildFile << """
                apply plugin: 'java-gradle-plugin'
                gradlePlugin {
                    plugins {
                        simplePlugin {
                            id = "org.gradle.sample.simple-plugin"
                            implementationClass = "org.gradle.sample.SimplePlugin"
                        }
                    }
                }
            """
        }
        def pluginSource = includedLibrary.file("src/main/java/org/gradle/sample/SimplePlugin.java")
        pluginSource << """
            package org.gradle.sample;
            import org.gradle.api.*;
            public class SimplePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getLogger().warn("Hello World");
                }
            }
        """

        settingsFile << """
            rootProject.name = "root"
            includeBuild "plugin"
        """
        buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:plugin:0.1'
                }
            }
            apply plugin: 'org.gradle.sample.simple-plugin'
        """

        when:
        succeeds("--status")
        succeeds("tasks")
        then:
        outputContains("Hello World")

        when:
        pluginSource.text = pluginSource.text.replace("Hello", "Goodbye")
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye World")

        cleanup:
        stopGradle()
    }

    def "will rebuild on change for build included into a multi-project build"() {
        def includedLibrary = singleProjectBuild("library") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def librarySource = includedLibrary.file("src/main/java/org/test/Library.java")
        librarySource << """
            package org.test;
            public class Library {
                public static void print(String who) {
                    System.out.println("Hello " + who);
                }
            }
        """

        settingsFile << """
            rootProject.name = "root"
            includeBuild "library"
            include "sub1"
            include "sub2"
        """
        buildFile << """
            subprojects {
                apply plugin: 'java'
                apply plugin: 'application'
                group = 'com.example'
                application {
                   mainClass = 'com.example.' + name + '.Main'
                }
                dependencies {
                    implementation 'org.test:library:0.1'
                }
            }
            project(":sub2") {
                run.mustRunAfter ":sub1:run"
            }
        """
        def mainSourceSub1 = file("sub1/src/main/java/com/example/sub1/Main.java")
        mainSourceSub1 << """
            package com.example.sub1;

            public class Main {
                public static void main(String... args) {
                    org.test.Library.print("First");
                }
            }
        """
        def mainSourceSub2 = file("sub2/src/main/java/com/example/sub2/Main.java")
        mainSourceSub2 << """
            package com.example.sub2;

            public class Main {
                public static void main(String... args) {
                    org.test.Library.print("Second");
                }
            }
        """
        when:
        succeeds("run")
        then:
        outputContains("Hello First")
        outputContains("Hello Second")

        when:
        librarySource.text = librarySource.text.replace("Hello", "Goodbye")
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye First")
        outputContains("Goodbye Second")

        when:
        mainSourceSub1.text = mainSourceSub1.text.replace("First", '1st')
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye 1st")
        outputContains("Goodbye Second")

        when:
        mainSourceSub2.text = mainSourceSub2.text.replace("Second", '2nd')
        then:
        buildTriggeredAndSucceeded()
        outputContains("Goodbye 1st")
        outputContains("Goodbye 2nd")
    }
}
