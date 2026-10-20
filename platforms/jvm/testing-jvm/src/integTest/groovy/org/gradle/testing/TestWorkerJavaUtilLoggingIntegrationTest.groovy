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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

class TestWorkerJavaUtilLoggingIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {

    @Issue("https://github.com/gradle/gradle/issues/39359")
    def "root handlers installed by a custom LogManager keep receiving records in tests"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }
            ${mavenCentralRepository()}
            testing.suites.test {
                useJUnitJupiter()
                targets.configureEach {
                    testTask.configure {
                        systemProperty("java.util.logging.manager", "example.RootHandlerOwningLogManager")
                    }
                }
            }
        """

        // Mirrors LogManagers like JBoss LogManager. The manager attaches its own handler
        // to the root logger when it creates it, and does nothing on reset().
        file("src/test/java/example/RootHandlerOwningLogManager.java") << """
            package example;

            import java.util.logging.Handler;
            import java.util.logging.LogManager;
            import java.util.logging.LogRecord;
            import java.util.logging.Logger;

            public class RootHandlerOwningLogManager extends LogManager {
                static final Handler MARKER = new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        System.out.println("marker received: " + record.getMessage());
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                };

                public RootHandlerOwningLogManager() {
                }

                @Override
                public boolean addLogger(Logger logger) {
                    boolean added = super.addLogger(logger);
                    if (added && logger.getName().isEmpty()) {
                        logger.addHandler(MARKER);
                    }
                    return added;
                }

                @Override
                public void reset() {
                }
            }
        """

        file("src/test/java/example/LoggingTest.java") << """
            package example;

            import java.util.logging.Logger;
            import org.junit.jupiter.api.Test;

            class LoggingTest {
                @Test
                void logs() {
                    Logger.getLogger("example").warning("hello");
                }
            }
        """

        when:
        succeeds("test")

        then:
        resultsFor()
            .testPath("example.LoggingTest", "logs()")
            .onlyRoot()
            .assertStdout(containsString("marker received: hello"))
    }

}
