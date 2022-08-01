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

package org.gradle.testing

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires

class MinimumJavaTestVersionIntegrationTest extends AbstractIntegrationSpec {

    @Requires(adhoc = { AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7) != null })
    def "Can run tests on java 7"() {
        def java7jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_7)
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing.suites.test.useJUnit()

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(7))
                }
            }
        """

        file("src/test/java/com/example/MyTest.java") << """
            package com.example;
            import org.junit.Test;

            public class MyTest {
                @Test
                public void doTest() {
                    assert System.getProperty("java.version").equals("7");
                }
            }
        """

        when:
        executer.withArgument("-Porg.gradle.java.installations.paths=" + java7jdk.javaHome.absolutePath)

        then:
        succeeds("test")
    }

}
