/*
 * Copyright 2022 the original author or authors.
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
import spock.lang.Ignore

/**
 * Tests that {@code JvmComponentDependencies#projectInternalView()} behaves as expected.
 */
@Ignore("removed from 8.0")
class ProjectInternalViewDependencyIntegrationTest extends AbstractIntegrationSpec {

    def "can test against the internal view of java application"() {
        given:
        buildFile << """
            plugins {
                id 'application'
            }

            dependencies {
                runtimeOnly "com.h2database:h2:2.1.214"
                implementation "com.fasterxml.jackson.core:jackson-databind:2.13.3"
                compileOnly "org.apache.commons:commons-lang3:3.12.0"
            }
        """
        writeBaseBuildFile()
        writeNonApiAccessibilityTest()

        when:
        succeeds "customTest"

        then:
        result.assertTaskExecuted(":jar")
    }

    def "can test against the internal view of java library"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            dependencies {
                runtimeOnly "com.h2database:h2:2.1.214"
                implementation "com.fasterxml.jackson.core:jackson-databind:2.13.3"
                compileOnly "org.apache.commons:commons-lang3:3.12.0"

                api "org.springframework:spring-core:5.3.22"
                compileOnlyApi "com.google.guava:guava:31.1-jre"
            }
        """
        writeBaseBuildFile()
        writeNonApiAccessibilityTest()
        writeApiAccessibilityTest()

        when:
        succeeds "customTest"

        then:
        result.assertTaskExecuted(":jar")
    }

    def writeBaseBuildFile() {
        buildFile("""
            ${mavenCentralRepository()}

            testing {
                suites {
                    customTest(JvmTestSuite) {
                        dependencies {
                            implementation projectInternalView()
                        }
                        useJUnit()
                    }
                }
            }
        """)
    }

    def writeNonApiAccessibilityTest() {
        file ("src/main/java/com/example/MyClass.java") << """
            package com.example;
            public class MyClass {
                public int getSpecialValue() {
                    return 13;
                }
            }
        """
        file("src/customTest/java/com/example/SuccessfulNonApiAccessibilityTest.java") << """
            package com.example;
            public class SuccessfulNonApiAccessibilityTest {
                @org.junit.Test
                public void verifyNonApiConfigurationsAreAccessible() throws Exception {
                    // project outputs are available
                    assert new MyClass().getSpecialValue() == 13;

                    // `implementation` dependencies are accessible
                    assert new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\\\"hello\\\": \\\"test\\\"}").get("hello").asText().equals("test");

                    // `compileOnly` dependencies are accessible at compile-time
                    try {
                        assert org.apache.commons.lang3.StringUtils.length("Hello, Gradle!") == 14;
                    } catch (NoClassDefFoundError e) {
                        // Expected
                    }

                    // `runtimeOnly` dependencies are accessible at runtime
                    assert getClass().getClassLoader().loadClass("org.h2.Driver") != null;
                }
            }
        """
    }

    def writeApiAccessibilityTest() {
        file("src/customTest/java/com/example/SuccessfulApiAccessibilityTest.java") << """
            package com.example;
            public class SuccessfulApiAccessibilityTest {
                @org.junit.Test
                public void verifyApiConfigurationsAreAccessible() throws Exception {
                    // `api` dependencies are accessible
                    assert new org.springframework.core.io.ByteArrayResource("Hello, Test!".getBytes()).contentLength() == 12;

                    // `compileOnlyApi` dependencies are accessible at compile-time
                    try {
                        assert com.google.common.collect.ImmutableList.of("123").equals("123");
                    } catch (NoClassDefFoundError e) {
                        // Expected
                    }
                }
            }
        """
    }
}
