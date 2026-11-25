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
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.testing.fixture.JUnitCoverage

class TestFilterAndLoggingLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {


    private static final String PRODUCER = """
        abstract class Producer extends DefaultTask {
            @Input
            abstract Property<String> getContent()

            @OutputFile
            abstract RegularFileProperty getOutputFile()

            @TaskAction
            void run() {
                outputFile.get().asFile.text = content.get()
            }
        }
    """

    def setup() {
        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.junit.Test
                public void foo() {
                    System.out.println("running FooTest");
                }
            }
        """
        file("src/test/java/BarTest.java") << """
            public class BarTest {
                @org.junit.Test
                public void bar() {
                    System.out.println("running BarTest");
                }
            }
        """
    }

    private void groovyBuild(String content) {
        buildFile """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}' }
            $PRODUCER
            $content
        """
    }

    private Set<String> executedTestClasses() {
        file("build/test-results/test").listFiles()
            .findAll { it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .collect { it.name - "TEST-" - ".xml" } as Set
    }

    def "eager filter values are stored in the lazy properties"() {
        groovyBuild """
            tasks.test.filter {
                includeTestsMatching("Foo*")
                includeTest("BarTest", "bar")
                excludeTestsMatching("*Other")
                failOnNoMatchingTests = false
            }
            def lazy = tasks.test.lazyProperties.filter
            println("lazy: " + [lazy.includePatterns.get(), lazy.excludePatterns.get(), lazy.failOnNoMatchingTests.get()])
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [[Foo*, BarTest.bar], [*Other], false]")
    }

    def "filter values set lazily are read by the eager getters and select the tests"() {
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add("FooTest")
                lazyProperties.filter.excludePatterns.add(providers.provider { "*Other" })
                println("eager: " + [filter.includePatterns, filter.excludePatterns, filter.failOnNoMatchingTests])
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [[FooTest], [*Other], true]")
        executedTestClasses() == ["FooTest"] as Set
    }

    def "eager additions keep lazily set patterns and the eager setter replaces them"() {
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add("FooTest")
                filter.includeTestsMatching("BarTest")
                println("added: " + filter.includePatterns)
                filter.setIncludePatterns("BarTest")
                println("replaced: " + lazyProperties.filter.includePatterns.get())
                filter.includePatterns.add("FooTest")
                println("view: " + lazyProperties.filter.includePatterns.get())
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("added: [FooTest, BarTest]")
        outputContains("replaced: [BarTest]")
        outputContains("view: [BarTest, FooTest]")
    }

    def "filter and logging lazy properties can be used from the Kotlin DSL"() {
        buildKotlinFile """
            import org.gradle.api.tasks.testing.logging.TestLogEvent

            plugins { java }
            ${mavenCentralRepository(org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN)}
            dependencies { testImplementation("junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}") }

            tasks.test {
                lazyProperties.filter.includePatterns.add("FooTest")
                lazyProperties.filter.failOnNoMatchingTests = false
                lazyProperties.testLogging.events.add(TestLogEvent.PASSED)
                lazyProperties.testLogging.info.minGranularity = 3
                println("eager: " + listOf(filter.includePatterns, filter.isFailOnNoMatchingTests, testLogging.events, testLogging.info.minGranularity))
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [[FooTest], false, [FAILED, PASSED], 3]")
        outputContains("FooTest > foo PASSED")
        executedTestClasses() == ["FooTest"] as Set
    }

    def "command line patterns are combined with lazily set patterns as before"() {
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add(providers.gradleProperty("pattern"))
            }
        """

        when:
        succeeds("test", "-Ppattern=*Test", "--tests", "FooTest")

        then:
        executedTestClasses() == ["FooTest"] as Set

        when:
        fails("test", "-Ppattern=BarTest", "--tests", "FooTest")

        then:
        failure.assertHasCause("No tests found for given includes: [BarTest](filter.includeTestsMatching) [FooTest](--tests filter)")
    }

    def "include pattern bound to a task output runs that task first"() {
        groovyBuild """
            def producer = tasks.register("producer", Producer) {
                content = "BarTest"
                outputFile = layout.buildDirectory.file("pattern.txt")
            }
            tasks.test {
                lazyProperties.filter.includePatterns.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":producer", ":test")
        executedTestClasses() == ["BarTest"] as Set
    }

    def "an empty pattern bound lazily fails when the task runs"() {
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add(providers.provider { "" })
            }
        """

        when:
        fails("test")

        then:
        failure.assertHasCause("Selected test name cannot be null or empty.")
    }

    def "an empty pattern fails in the eager method"() {
        groovyBuild """
            tasks.test.filter.includeTestsMatching("")
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Selected test name cannot be null or empty.")
    }

    def "up-to-date check reacts to a lazy filter change"() {
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add(providers.gradleProperty("pattern"))
            }
        """

        when:
        succeeds("test", "-Ppattern=FooTest")

        then:
        executedAndNotSkipped(":test")

        when:
        succeeds("test", "-Ppattern=FooTest")

        then:
        skipped(":test")

        when:
        succeeds("test", "-Ppattern=BarTest")

        then:
        executedAndNotSkipped(":test")
        executedTestClasses() == ["BarTest"] as Set
    }

    def "filter input properties keep their names and values"() {
        groovyBuild """
            tasks.test {
                filter.includeTestsMatching("FooTest")
                lazyProperties.filter.excludePatterns.add("*Other")
                doLast {
                    println("filter inputs: " + inputs.properties.findAll { it.key.startsWith("filter.") }.sort())
                }
            }
        """

        when:
        succeeds("test", "--tests", "Foo*")

        then:
        outputContains("filter inputs: [filter.commandLineIncludePatterns:[Foo*], filter.excludePatterns:[*Other], filter.failOnNoMatchingTests:true, filter.includePatterns:[FooTest]]")
    }

    def "eager logging values are stored in the lazy properties of their log level"() {
        groovyBuild """
            tasks.test.testLogging {
                events "skipped"
                exceptionFormat = "full"
                showStandardStreams = true
                info.minGranularity = 3
                debug { stackTraceFilters "groovy" }
            }
            def lazy = tasks.test.lazyProperties.testLogging
            println("lifecycle: " + [lazy.events.get(), lazy.exceptionFormat.get(), lazy.lifecycle.is(lazy)])
            println("info: " + [lazy.info.events.get(), lazy.info.minGranularity.get()])
            println("debug: " + lazy.debug.stackTraceFilters.get())
            println("defaults: " + [lazy.error.events.get(), lazy.error.exceptionFormat.get(), lazy.error.stackTraceFilters.get()])
        """

        when:
        succeeds("help")

        then:
        outputContains("lifecycle: [[FAILED, SKIPPED, STANDARD_OUT, STANDARD_ERROR], FULL, true]")
        outputContains("info: [[SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR], 3]")
        outputContains("debug: [GROOVY]")
        outputContains("defaults: [[], FULL, [TRUNCATE]]")
    }

    def "logging values set lazily are read by the eager getters and change what is logged"() {
        groovyBuild """
            tasks.test {
                lazyProperties.testLogging.events.add(providers.provider { org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED })
                println("eager: " + [testLogging.events, testLogging.lifecycle.events, testLogging.showStandardStreams])
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [[FAILED, PASSED], [FAILED, PASSED], false]")
        outputContains("FooTest > foo PASSED")
        outputContains("BarTest > bar PASSED")
    }

    def "showStandardStreams is derived from the lazy events"() {
        groovyBuild """
            tasks.test {
                lazyProperties.testLogging.events.addAll(
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
                )
                println("showStandardStreams: " + testLogging.showStandardStreams)
                testLogging.showStandardStreams = false
                println("events: " + lazyProperties.testLogging.events.get())
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("showStandardStreams: true")
        outputContains("events: [FAILED]")
    }

    def "logging property bound to a task output does not run that task, because the logging getters are @Internal"() {
        groovyBuild """
            def producer = tasks.register("producer", Producer) {
                content = "PASSED"
                outputFile = layout.buildDirectory.file("event.txt")
            }
            tasks.test {
                lazyProperties.testLogging.events.add(producer.flatMap { it.outputFile }.map {
                    org.gradle.api.tasks.testing.logging.TestLogEvent.valueOf(it.asFile.text)
                })
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        fails("test")

        then:
        result.assertTasksNotScheduled(":producer")
        // The provider fails while the logger handles a test event, so the reported failure is an internal test reporting error
        failureDescriptionStartsWith("Execution failed for task ':test'")
    }

    def "logging property bound to a task output works with an explicit dependency"() {
        groovyBuild """
            def producer = tasks.register("producer", Producer) {
                content = "PASSED"
                outputFile = layout.buildDirectory.file("event.txt")
            }
            tasks.test {
                dependsOn(producer)
                lazyProperties.testLogging.events.add(producer.flatMap { it.outputFile }.map {
                    org.gradle.api.tasks.testing.logging.TestLogEvent.valueOf(it.asFile.text)
                })
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("FooTest > foo PASSED")
    }

    def "convention mapping on the logging container becomes the convention of the lifecycle lazy property"() {
        groovyBuild """
            tasks.test {
                testLogging.conventionMapping.map("displayGranularity") { 5 }
                println("mapped: " + [testLogging.displayGranularity, lazyProperties.testLogging.displayGranularity.get()])
                lazyProperties.testLogging.displayGranularity = 1
                println("lazy: " + testLogging.displayGranularity)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped: [5, 5]")
        outputContains("lazy: 1")
    }

    def "a log level replaced with a setter no longer uses its lazy properties"() {
        groovyBuild """
            tasks.test {
                testLogging.info = testLogging.debug
                lazyProperties.testLogging.info.minGranularity = 4
                println("eager info: " + testLogging.info.minGranularity)
                println("lazy info: " + lazyProperties.testLogging.info.minGranularity.get())
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("eager info: 0")
        outputContains("lazy info: 4")
    }

    def "filter and logging values bound lazily survive the configuration cache"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        groovyBuild """
            tasks.test {
                lazyProperties.filter.includePatterns.add(providers.gradleProperty("pattern"))
                lazyProperties.testLogging.events.add(org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED)
            }
        """

        when:
        succeeds("test", "--configuration-cache", "-Ppattern=FooTest")

        then:
        configurationCache.assertStateStored()
        outputContains("FooTest > foo PASSED")
        executedTestClasses() == ["FooTest"] as Set

        when:
        file("build/test-results").deleteDir()
        succeeds("test", "--configuration-cache", "-Ppattern=FooTest")

        then:
        configurationCache.assertStateLoaded()
        outputContains("FooTest > foo PASSED")
        executedTestClasses() == ["FooTest"] as Set
    }
}
