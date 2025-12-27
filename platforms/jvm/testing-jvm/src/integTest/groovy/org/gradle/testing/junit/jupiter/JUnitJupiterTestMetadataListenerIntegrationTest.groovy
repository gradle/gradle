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

package org.gradle.testing.junit.jupiter

import org.gradle.api.tasks.testing.TestMetadataListener
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Ignore

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

/**
 * Integration tests for using {@link TestMetadataListener} with JUnit Jupiter tests.
 */
@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterTestMetadataListenerIntegrationTest extends AbstractTestingMultiVersionIntegrationTest implements JUnitJupiterMultiVersionTest  {
    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            test.${configureTestFramework}
        """.stripIndent()
    }

    def "can register metadata listener for tests"() {
        given:
        javaFile("src/test/java/SomeTest.java", """
            ${testFrameworkImports}
            import org.junit.jupiter.api.TestReporter;

            public class SomeTest {
                @Test
                public void successful(TestReporter testReporter) {
                    testReporter.publishEntry("myKey", "myValue");
                }

                @Test
                public void failing(TestReporter testReporter) {
                    testReporter.publishEntry("myKey2", "myValue2");
                    assertTrue(false);
                }
            }
        """.stripIndent())

        buildFile("""
            tasks.test {
                addTestMetadataListener(new LoggingMetadataListener(logger: project.logger))

                def removeMe = new RemoveMeListener()
                addTestMetadataListener(removeMe)
                removeTestMetadataListener(removeMe)
            }

            class LoggingMetadataListener implements TestMetadataListener {
                private logger

                void onMetadata(TestDescriptor descriptor, TestMetadataEvent event) {
                    logger.lifecycle(descriptor.toString() + " with values: " + event.values)
                }
            }

            class RemoveMeListener implements TestMetadataListener {
                void onMetadata(TestDescriptor descriptor, TestMetadataEvent event) {
                    println "remove me!"
                }
            }
        """)

        when:
        def failure = executer.withTasks('test').runWithFailure()

        then:
        failure.output.contains("Test successful(TestReporter)(SomeTest) with values: [myKey:myValue]")
        failure.output.contains("Test failing(TestReporter)(SomeTest) with values: [myKey2:myValue2]")

        !failure.output.contains("remove me!")
    }

    def "can register metadata listener for tests in kotlin DSL"() {
        given:
        buildFile.delete()
        settingsFile.delete()

        javaFile("src/test/java/SomeTest.java", """
            ${testFrameworkImports}
            import org.junit.jupiter.api.TestReporter;

            public class SomeTest {
                @Test
                public void successful(TestReporter testReporter) {
                    testReporter.publishEntry("myKey", "myValue");
                }

                @Test
                public void failing(TestReporter testReporter) {
                    testReporter.publishEntry("myKey2", "myValue2");
                    assertTrue(false);
                }
            }
        """.stripIndent())

        settingsKotlinFile << """
            rootProject.name = "test-metadata-listener"
        """

        buildKotlinFile("""
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.2")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.2")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.named<Test>("test").configure {
                ${configureTestFramework}

                addTestMetadataListener(LoggingMetadataListener(project.logger))

                val removeMe = RemoveMeListener()
                addTestMetadataListener(removeMe)
                removeTestMetadataListener(removeMe)
            }

            class LoggingMetadataListener(val logger: Logger) : TestMetadataListener {
                override fun onMetadata(descriptor: TestDescriptor , event: TestMetadataEvent) {
                    if (event is TestKeyValueDataEvent) {
                        logger.lifecycle(descriptor.toString() + " with values: " + event.getValues())
                    }
                }
            }

            class RemoveMeListener : TestMetadataListener {
                override fun onMetadata(descriptor: TestDescriptor , event: TestMetadataEvent) {
                    println("remove me!")
                }
            }
        """)

        when:
        def failure = executer.withTasks('test').runWithFailure()

        then:
        failure.output.contains("Test successful(TestReporter)(SomeTest) with values: {myKey=myValue}")
        failure.output.contains("Test failing(TestReporter)(SomeTest) with values: {myKey2=myValue2}")

        !failure.output.contains("remove me!")
    }

    // Ideally, this test should fail as coded, but currently it doesn't since we have to leave
    // the TestMetadataListener registered at the build level
    // instead of the project level.  Both this and TestOutputListener should be fixed together.
    @Ignore
    @UnsupportedWithConfigurationCache
    def "can register metadata listener at gradle level"() {
        given:
        javaFile("src/test/java/SomeTest.java", """
            ${testFrameworkImports}
            import org.junit.jupiter.api.TestReporter;

            public class SomeTest {
                @Test
                public void successful(TestReporter testReporter) {
                    testReporter.publishEntry("myKey", "myValue");
                }
            }
        """.stripIndent())

        buildFile << """
            gradle.addListener(new LoggingMetadataListener(logger: project.logger))

            class LoggingMetadataListener implements TestMetadataListener {
                private logger

                public void onMetadata(TestDescriptor descriptor, TestMetadataEvent event) {
                    logger.lifecycle("From listener: " + descriptor.toString() + " with values: " + event.values);
                }
            }
        """.stripIndent()

        when:
        fails('test')

        then:
        failureDescriptionContains("Execution failed for task ':test'.")
        failureCauseContains("Listener type org.gradle.api.tasks.testing.TestMetadataListener with service scope 'Project' cannot be used to generate events in scope 'Build'.")
    }
}
