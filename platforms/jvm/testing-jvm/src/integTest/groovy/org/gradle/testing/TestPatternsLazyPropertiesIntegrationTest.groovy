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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Simulates a work layer that binds and updates the class file patterns of {@code Test} through
 * {@code lazyProperties.classFilePatterns} only.
 */
class TestPatternsLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            import org.gradle.api.tasks.util.FilePatterns

            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:4.13.2' }
            tasks.test {
                testLogging.showStandardStreams = true
            }

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
        ["FooTest", "BarTest"].each { name ->
            file("src/test/java/${name}.java") << """
                public class ${name} {
                    @org.junit.Test
                    public void run() {
                        System.out.println("running ${name}");
                    }
                }
            """
        }
    }

    def "binding replaces the patterns set eagerly by a plugin"() {
        buildFile """
            tasks.test {
                exclude("**/FooTest.class")
                exclude { it.name == "FooTest.class" }
                lazyProperties.classFilePatterns.set(FilePatterns.empty().include("**/FooTest.class"))
                println("includes: " + includes + " excludes: " + excludes)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("includes: [**/FooTest.class] excludes: []")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")
    }

    def "an eager exclude after a lazy binding adds to it without evaluating the binding"() {
        buildFile """
            tasks.test {
                lazyProperties.classFilePatterns.set(providers.provider {
                    println("evaluating bound patterns")
                    FilePatterns.empty().include("**/*Test.class")
                })
                exclude("**/BarTest.class")
            }
        """

        when:
        succeeds("test")

        then:
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")
    }

    def "patterns bound to the output of another task schedule that task"() {
        buildFile """
            def producer = tasks.register("producer", WritePattern) {
                content = "**/BarTest.class"
                outputFile = layout.buildDirectory.file("pattern.txt")
            }
            tasks.test {
                lazyProperties.classFilePatterns.set(producer.flatMap { it.outputFile }.map { f ->
                    println("evaluating bound patterns")
                    FilePatterns.empty().exclude(f.asFile.text.trim())
                })
            }
        """

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":producer", ":test")
        output.indexOf("evaluating bound patterns") > output.indexOf("task graph ready")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")

        when:
        file("build").deleteDir()
        succeeds("test")

        then:
        executedAndNotSkipped(":producer", ":test")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")
    }

    def "changing the bound patterns makes the task out of date"() {
        buildFile """
            tasks.test {
                lazyProperties.classFilePatterns.set(providers.gradleProperty("only").map { FilePatterns.empty().include(it) })
            }
        """

        when:
        succeeds("test", "-Ponly=**/FooTest.class")

        then:
        executedAndNotSkipped(":test")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")

        when:
        succeeds("test", "-Ponly=**/FooTest.class")

        then:
        skipped(":test")

        when:
        succeeds("test", "-Ponly=**/BarTest.class")

        then:
        executedAndNotSkipped(":test")
        outputContains("running BarTest")
        outputDoesNotContain("running FooTest")
    }

    def "the no matching tests message shows the bound patterns"() {
        buildFile """
            tasks.test {
                lazyProperties.classFilePatterns.set(providers.provider { FilePatterns.empty().include("**/FooTest.class") })
                exclude("**/BarTest.class")
                filter.includeTestsMatching("Missing")
            }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("No tests found for given includes: [**/FooTest.class](include rules) [**/BarTest.class](exclude rules) [Missing](filter.includeTestsMatching)")
    }

    def "the getters return live views and an exclude in doFirst applies"() {
        buildFile """
            tasks.test {
                includes << "**/*Test.class"
                excludes.add("**/FooTest.class")
                println("equals plain set: " + (includes == ["**/*Test.class"] as Set))
                excludes.clear()
                println("lazy: " + lazyProperties.classFilePatterns.get().includes + " " + lazyProperties.classFilePatterns.get().excludes)
                doFirst { exclude("**/BarTest.class") }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("equals plain set: true")
        outputContains("lazy: [**/*Test.class] []")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")
    }

    def "a convention on the lazy property is kept by an eager exclude"() {
        buildFile """
            tasks.test {
                lazyProperties.classFilePatterns.convention(providers.provider {
                    println("evaluating convention")
                    FilePatterns.empty().include("**/*Test.class")
                })
                exclude("**/BarTest.class")
            }
        """

        when:
        succeeds("test")

        then:
        output.indexOf("evaluating convention") > output.indexOf("task graph ready")
        outputContains("running FooTest")
        outputDoesNotContain("running BarTest")
    }
}
