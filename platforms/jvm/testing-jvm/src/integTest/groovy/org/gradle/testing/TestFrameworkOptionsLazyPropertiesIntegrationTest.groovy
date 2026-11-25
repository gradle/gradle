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
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.fixture.TestNGCoverage

class TestFrameworkOptionsLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

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

    private Set<String> executedTests() {
        def dir = file("build/test-results/test")
        dir.listFiles()
            .findAll { it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .collectMany { xml -> new groovy.xml.XmlSlurper().parse(xml).testcase.collect { it.@name.text() } } as Set
    }

    private void junitPlatformProject(String content) {
        buildFile """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            $PRODUCER
            $content
        """
        file("src/test/java/TaggedTest.java") << """
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            public class TaggedTest {
                @Test @Tag("fast") public void fast() {}
                @Test @Tag("slow") public void slow() {}
            }
        """
    }

    private void junitProject(String content) {
        buildFile """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}' }
            $content
        """
        file("src/test/java/Fast.java") << "public interface Fast {}"
        file("src/test/java/Slow.java") << "public interface Slow {}"
        file("src/test/java/CategorizedTest.java") << """
            import org.junit.Test;
            import org.junit.experimental.categories.Category;

            public class CategorizedTest {
                @Test @Category(Fast.class) public void fast() {}
                @Test @Category(Slow.class) public void slow() {}
            }
        """
    }

    private void testNGProject(String content) {
        buildFile """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:${TestNGCoverage.NEWEST}' }
            $content
        """
        file("src/test/java/GroupedTest.java") << """
            import org.testng.annotations.Test;

            public class GroupedTest {
                @Test(groups = "fast") public void fast() {}
                @Test(groups = "slow") public void slow() {}
            }
        """
    }

    def "JUnit Platform tags set eagerly are stored in the lazy properties"() {
        junitPlatformProject """
            tasks.test.useJUnitPlatform {
                includeTags "fast"
                excludeTags = ["slow"] as Set
                includeEngines("junit-jupiter")
            }
            def lazy = tasks.test.options.lazyProperties
            println("lazy: " + [lazy.includeTags.get(), lazy.excludeTags.get(), lazy.includeEngines.get(), lazy.excludeEngines.get()])
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [[fast], [slow], [junit-jupiter], []]")
    }

    def "JUnit Platform tags set lazily are read by the eager getters and select the tests"() {
        junitPlatformProject """
            tasks.test.useJUnitPlatform {
                lazyProperties.includeTags.add("fast")
                println("eager: " + includeTags)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [fast]")
        executedTests() == ["fast()"] as Set
    }

    def "JUnit Platform tags can be set lazily from the Kotlin DSL"() {
        buildKotlinFile """
            plugins { java }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }
            tasks.test {
                useJUnitPlatform {
                    lazyProperties.includeTags.add("fast")
                    lazyProperties.excludeEngines = setOf("junit-vintage")
                }
                println("eager: " + (options as JUnitPlatformOptions).includeTags + " " + (options as JUnitPlatformOptions).excludeEngines)
            }
        """
        file("src/test/java/TaggedTest.java") << """
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            public class TaggedTest {
                @Test @Tag("fast") public void fast() {}
                @Test @Tag("slow") public void slow() {}
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [fast] [junit-vintage]")
        executedTests() == ["fast()"] as Set
    }

    def "JUnit categories set lazily select the tests"() {
        junitProject """
            tasks.test.useJUnit {
                lazyProperties.includeCategories.add("Fast")
                println("eager: " + includeCategories)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [Fast]")
        executedTests() == ["fast"] as Set
    }

    def "JUnit categories set eagerly are stored in the lazy properties, and setting null reads null"() {
        junitProject """
            tasks.test.useJUnit {
                includeCategories "Fast"
                excludeCategories("Slow")
                println("lazy: " + [lazyProperties.includeCategories.get(), lazyProperties.excludeCategories.get()])
                includeCategories = null
                lazyProperties.includeCategories.convention(["Other"])
                println("null: " + includeCategories)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [[Fast], [Slow]]")
        outputContains("null: null")
    }

    def "TestNG groups set lazily select the tests"() {
        testNGProject """
            tasks.test.useTestNG {
                lazyProperties.includeGroups.add("fast")
                println("eager: " + includeGroups)
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("eager: [fast]")
        executedTests() == ["fast"] as Set
    }

    def "TestNG values set eagerly are stored in the lazy properties"() {
        testNGProject """
            tasks.test.useTestNG {
                includeGroups "fast"
                excludeGroups "slow"
                listeners = ["a.Listener"] as Set
                configFailurePolicy = "continue"
                parallel = "methods"
                threadCount = 3
                useDefaultListeners = true
                threadPoolFactoryClass = "a.Factory"
                suiteName = "Suite"
                testName = "Name"
                suites("suite.xml")
                preserveOrder = true
                groupByInstances = true
                def l = lazyProperties
                println("lazy: " + [l.includeGroups.get(), l.excludeGroups.get(), l.listeners.get(), l.configFailurePolicy.get(), l.parallel.get(),
                    l.threadCount.get(), l.useDefaultListeners.get(), l.threadPoolFactoryClass.get(), l.suiteName.get(), l.testName.get(),
                    l.suiteXmlFiles.get().collect { it.name }, l.preserveOrder.get(), l.groupByInstances.get()])
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: [[fast], [slow], [a.Listener], continue, methods, 3, true, a.Factory, Suite, Name, [suite.xml], true, true]")
    }

    def "TestNG values set lazily are read by the eager getters, and setting null reads null"() {
        testNGProject """
            tasks.test.useTestNG {
                lazyProperties.suiteName = "Lazy suite"
                lazyProperties.threadCount = 2
                lazyProperties.preserveOrder = true
                lazyProperties.suiteXmlFiles.add(file("suite.xml"))
                println("eager: " + [suiteName, threadCount, preserveOrder, isPreserveOrder(), suiteXmlFiles.collect { it.name }])
                println("defaults: " + [testName, configFailurePolicy, parallel, useDefaultListeners, groupByInstances])
                println("output dir follows the html report: " + (outputDirectory == tasks.test.reports.html.outputLocation.get().asFile))
                parallel = null
                lazyProperties.parallel.convention("methods")
                println("null: " + parallel)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("eager: [Lazy suite, 2, true, true, [suite.xml]]")
        outputContains("defaults: [Gradle test, skip, null, false, false]")
        outputContains("output dir follows the html report: true")
        outputContains("null: null")
    }

    def "swapping the test framework drops the options and their lazy bindings, as it drops eager values"() {
        junitPlatformProject """
            def test = tasks.test
            test.useJUnitPlatform()
            def before = test.options
            before.lazyProperties.includeTags.add("fast")
            test.useJUnitPlatform()
            println("same framework keeps options: " + test.options.is(before) + " " + test.options.includeTags)
            test.useTestNG()
            println("options after swap: " + test.options.class.simpleName.replace('_Decorated', '') + " " + test.options.includeGroups)
            test.useJUnitPlatform()
            println("new JUnit Platform options: " + test.options.is(before) + " " + test.options.includeTags)
            println("old binding is detached: " + before.lazyProperties.includeTags.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("same framework keeps options: true [fast]")
        outputContains("options after swap: TestNGOptions []")
        outputContains("new JUnit Platform options: false []")
        outputContains("old binding is detached: [fast]")
    }

    def "copyFrom shares the lazy values of the source"() {
        junitPlatformProject """
            def source = objects.newInstance(JUnitPlatformOptions)
            def target = objects.newInstance(JUnitPlatformOptions)
            source.includeTags("fast")
            target.includeTags("other")
            target.copyFrom(source)
            println("copied: " + target.includeTags)
            source.includeTags("later")
            println("after source change: " + target.includeTags)
        """

        when:
        succeeds("help")

        then:
        outputContains("copied: [fast]")
        outputContains("after source change: [fast, later]")
    }

    def "tag bound to a task output runs that task first"() {
        junitPlatformProject """
            def producer = tasks.register("producer", Producer) {
                content = "slow"
                outputFile = layout.buildDirectory.file("tag.txt")
            }
            tasks.test.useJUnitPlatform {
                lazyProperties.includeTags.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":producer", ":test")
        executedTests() == ["slow()"] as Set
    }

    def "tag bound to a task output runs that task first, also when the configuration cache is reused"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        junitPlatformProject """
            def producer = tasks.register("producer", Producer) {
                content = "slow"
                outputFile = layout.buildDirectory.file("tag.txt")
            }
            tasks.test.useJUnitPlatform {
                lazyProperties.includeTags.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            }
        """

        when:
        succeeds("test", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        result.assertTaskOrder(":producer", ":test")
        executedTests() == ["slow()"] as Set

        when:
        file("build/tag.txt").delete()
        file("build/test-results").deleteDir()
        succeeds("test", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskOrder(":producer", ":test")
        executedTests() == ["slow()"] as Set
    }

    def "up-to-date check reacts to a lazy tag change"() {
        junitPlatformProject """
            tasks.test.useJUnitPlatform {
                lazyProperties.includeTags.add(providers.gradleProperty("tag"))
            }
        """

        when:
        succeeds("test", "-Ptag=fast")

        then:
        executedAndNotSkipped(":test")

        when:
        succeeds("test", "-Ptag=fast")

        then:
        skipped(":test")

        when:
        succeeds("test", "-Ptag=slow")

        then:
        executedAndNotSkipped(":test")
        executedTests() == ["slow()"] as Set
    }

    def "option input properties keep their names and values"() {
        junitPlatformProject """
            tasks.test {
                useJUnitPlatform {
                    includeTags "fast"
                    lazyProperties.excludeEngines.add("junit-vintage")
                }
                doLast {
                    println("option inputs: " + inputs.properties.findAll { it.key.contains("options.") }.sort())
                }
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("option inputs: [options.excludeEngines:[junit-vintage], options.excludeTags:[], options.includeEngines:[], options.includeTags:[fast], " +
            "testFrameworkProperty.options.excludeEngines:[junit-vintage], testFrameworkProperty.options.excludeTags:[], testFrameworkProperty.options.includeEngines:[], testFrameworkProperty.options.includeTags:[fast]]")
    }
}
