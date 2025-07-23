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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class JavaCompilationOutputsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        file("src/main/java/com/example/Example.java") << """
            package com.example;

            public class Example {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        """
        file("src/main/java/com/example/Filtered.java") << """
            package com.example;

            public class Filtered {
            }
        """
        file("src/test/java/com/example/ExampleTest.java").java """
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            public class ExampleTest {
                @Test
                public void testMain() {
                    try {
                        Class.forName("com.example.Filtered");
                        assert false; // should not get here
                    } catch (ClassNotFoundException e) {
                        // This class should not be on the classpath
                        assertTrue(e.getMessage().contains("com.example.Filtered"));
                        return;
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
            /*
            change the compileJava output dir to an intermediate directory
            use that intermediate directory as input directory for my custom task
            use the original output directory (main/classes) as the output directory of my custom task
            finalize compileJava by calling my custom task
            */
            abstract class CustomTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getClasses()

                @OutputDirectory
                abstract DirectoryProperty getDestinationDirectory()

                @Inject
                abstract FileSystemOperations getFileOperations()

                @TaskAction
                void copy() {
                    fileOperations.sync {
                        from(classes)
                        exclude("**/Filtered.class")
                        into(destinationDirectory)
                    }
                }
            }
            tasks.register("copyIt", CustomTask) {
                classes.from(tasks.named("compileJava"))
                destinationDirectory.convention(sourceSets.main.java.destinationDirectory)
            }
            tasks.named("compileJava") {
                destinationDirectory = layout.buildDirectory.dir("other-place")
                finalizedBy(copyIt)
            }

            tasks.named("test") {
                // Use JUnit Platform for unit tests.
                useJUnitPlatform()
                def expectedDir = sourceSets.main.java.destinationDirectory
                doLast {
                    if (!classpath.contains(expectedDir.get().asFile)) {
                        throw new GradleException("Test classpath is not set correctly: " + classpath.asPath)
                    }
                }
            }
        """
    }
    @Issue("https://github.com/gradle/gradle/issues/34349")
    def "rewiring output of source set and compile tasks sort of works"() {
        buildFile << """
            tasks.named("classes") {
                dependsOn("copyIt")
            }
        """
        expect:
        succeeds("test")
    }

    @Issue("https://github.com/gradle/gradle/issues/34349")
    def "rewiring output of source set and compile tasks works with compiledBy"() {
        buildFile << """
            // This shouldn't be necessary with compiledBy, but it is because we
            // use 8.x behavior in JvmPluginsHelper instead of relying on dependency
            // information from classesDirectory
            tasks.named("classes") {
                dependsOn("copyIt")
            }

            sourceSets {
                main {
                    java {
                        compiledBy(tasks.named("copyIt"), { copyIt -> copyIt.destinationDirectory })
                    }
                }
            }
        """
        expect:
        succeeds("test")
    }
}
