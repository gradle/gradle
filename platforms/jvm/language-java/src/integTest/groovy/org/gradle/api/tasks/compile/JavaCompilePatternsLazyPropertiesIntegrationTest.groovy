/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Simulates a work layer that binds and updates the source patterns of {@code JavaCompile} through
 * {@code lazyProperties.sourcePatterns} only.
 */
class JavaCompilePatternsLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            import org.gradle.api.tasks.util.FilePatterns

            apply plugin: 'java'

            abstract class WritePattern extends DefaultTask {
                @Input
                abstract Property<String> getContent()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = content.get()
                }
            }

            gradle.taskGraph.whenReady { println("task graph ready") }
        """
        file("src/main/java/Main.java") << "public class Main {}"
        file("src/main/java/Other.java") << "public class Other {}"
        file("src/main/java/Bad.java") << "public class Bad { this does not compile }"
    }

    private void assertCompiled(String... classes) {
        def dir = file("build/classes/java/main")
        assert dir.list().findAll { it.endsWith(".class") }.sort() == classes.collect { it + ".class" }.sort()
    }

    def "binding replaces the patterns set eagerly by a plugin"() {
        buildFile """
            tasks.compileJava {
                include("**/Bad.java")
                lazyProperties.sourcePatterns.set(FilePatterns.empty().exclude("**/Bad.java"))
                println("includes: " + includes + " excludes: " + excludes)
            }
        """

        when:
        succeeds("compileJava")

        then:
        outputContains("includes: [] excludes: [**/Bad.java]")
        assertCompiled("Main", "Other")
    }

    def "an eager exclude after a lazy binding adds to it without evaluating the binding"() {
        buildFile """
            tasks.compileJava {
                lazyProperties.sourcePatterns.set(providers.provider {
                    println("evaluating bound patterns")
                    FilePatterns.empty().exclude("**/Other.java")
                })
                exclude("**/Bad.java")
            }
        """

        when:
        succeeds("compileJava")

        then:
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        assertCompiled("Main")
    }

    def "patterns bound to the output of another task schedule that task"() {
        buildFile """
            def producer = tasks.register("producer", WritePattern) {
                content = "**/Bad.java"
                outputFile = layout.buildDirectory.file("pattern.txt")
            }
            tasks.compileJava {
                lazyProperties.sourcePatterns.set(producer.flatMap { it.outputFile }.map { f ->
                    println("evaluating bound patterns")
                    FilePatterns.empty().exclude(f.asFile.text.trim())
                })
                exclude("**/Other.java")
            }
        """

        when:
        succeeds("compileJava")

        then:
        executedAndNotSkipped(":producer", ":compileJava")
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        assertCompiled("Main")

        when:
        file("build").deleteDir()
        succeeds("compileJava")

        then:
        executedAndNotSkipped(":producer", ":compileJava")
        assertCompiled("Main")
    }

    def "changing the bound patterns makes the task out of date"() {
        buildFile """
            tasks.compileJava {
                lazyProperties.sourcePatterns.set(providers.gradleProperty("skip").map { FilePatterns.empty().exclude("**/Bad.java", it) })
            }
        """

        when:
        succeeds("compileJava", "-Pskip=**/Other.java")

        then:
        executedAndNotSkipped(":compileJava")
        assertCompiled("Main")

        when:
        succeeds("compileJava", "-Pskip=**/Other.java")

        then:
        skipped(":compileJava")

        when:
        succeeds("compileJava", "-Pskip=**/Main.java")

        then:
        executedAndNotSkipped(":compileJava")
        assertCompiled("Other")
    }

    def "an exclude in doFirst applies to the sources that are compiled"() {
        buildFile """
            tasks.compileJava {
                doFirst { exclude("**/Bad.java") }
            }
        """

        when:
        succeeds("compileJava")

        then:
        assertCompiled("Main", "Other")
    }
}
