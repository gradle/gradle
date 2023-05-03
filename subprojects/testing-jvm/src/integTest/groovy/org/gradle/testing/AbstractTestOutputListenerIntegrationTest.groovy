/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

@Issue("GRADLE-1009")
abstract class AbstractTestOutputListenerIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def setup() {
        executer.noExtraLogging()
    }

    def "can use standard output listener for tests"() {
        given:
        file("src/test/java/SomeTest.java") << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void showsOutputWhenPassing() {
                    System.out.println("out passing");
                    System.err.println("err passing");
                    assertTrue(true);
                }

                @Test public void showsOutputWhenFailing() {
                    System.out.println("out failing");
                    System.err.println("err failing");
                    assertTrue(false);
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            test.${configureTestFramework}
            test.addTestOutputListener(new VerboseOutputListener(logger: project.logger))

            def removeMe = new RemoveMeListener()
            test.addTestOutputListener(removeMe)
            test.removeTestOutputListener(removeMe)

            class VerboseOutputListener implements TestOutputListener {

                def logger

                public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
                    logger.lifecycle(descriptor.toString() + " " + event.destination + " " + event.message);
                }
            }

            class RemoveMeListener implements TestOutputListener {
                public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
                    println "remove me!"
                }
            }
        """.stripIndent()

        when:
        def failure = executer.withTasks('test').runWithFailure()

        then:
        failure.output.contains("Test ${maybeParentheses('showsOutputWhenPassing')}(SomeTest) StdOut out passing")
        failure.output.contains("Test ${maybeParentheses('showsOutputWhenFailing')}(SomeTest) StdOut out failing")
        failure.output.contains("Test ${maybeParentheses('showsOutputWhenPassing')}(SomeTest) StdErr err passing")
        failure.output.contains("Test ${maybeParentheses('showsOutputWhenFailing')}(SomeTest) StdErr err failing")

        !failure.output.contains("remove me!")
    }

    @UnsupportedWithConfigurationCache
    def "can register output listener at gradle level and using onOutput method"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void foo() {
                    System.out.println("message from foo");
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            test.${configureTestFramework}

            test.onOutput { descriptor, event ->
                logger.lifecycle("first: " + event.message)
            }

            gradle.addListener(new VerboseOutputListener(logger: project.logger))

            class VerboseOutputListener implements TestOutputListener {

                def logger

                public void onOutput(TestDescriptor descriptor, TestOutputEvent event) {
                    logger.lifecycle("second: " + event.message);
                }
            }
        """.stripIndent()

        when:
        succeeds('test')

        then:
        outputContains('first: message from foo')
        outputContains('second: message from foo')
    }

    def "shows standard streams configured via closure"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void foo() {
                    System.out.println("message from foo");
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            test.${configureTestFramework}
            test.testLogging {
                showStandardStreams = true
            }
        """

        when:
        executer.withArgument('-i')
        succeeds('test')

        then:
        outputContains('message from foo')
    }
}
