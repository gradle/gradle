/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.testing;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import org.gradle.util.Requires;

public class TestTaskIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2702")
    def "should not resolve configuration results when there are no tests"() {
        buildFile << """
            apply plugin: 'java'

            configure([configurations.testRuntime, configurations.testCompile]) { configuration ->
                incoming.afterResolve {
                    assert configuration.resolvedState == org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.TASK_DEPENDENCIES_RESOLVED : "should not be resolved"
                }
            }
        """

        when:
        run("build")

        then:
        noExceptionThrown()
    }

    def "test task is skipped when there are no tests"() {
        buildFile << "apply plugin: 'java'"
        file("src/test/java/not_a_test.txt")

        when:
        run("build")

        then:
        result.assertTaskSkipped(":test")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compiles and executes a Java 9 test suite"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass()

        when:
        succeeds 'test'

        then:
        noExceptionThrown()

        and:
        classFormat('build/classes/test/MyTest.class') == 53

    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compiles and executes a Java 9 test suite even if a module descriptor is on classpath"() {
        given:
        buildFile << java9Build()

        file('src/test/java/MyTest.java') << standaloneTestClass()
        file('src/main/java/com/acme/Foo.java') << '''package com.acme;
            public class Foo {}
        '''
        file('src/main/java/com/acme/module-info.java') << '''module com.acme {
            exports com.acme;
        }'''

        when:
        succeeds 'test'

        then:
        noExceptionThrown()

        and:
        classFormat('build/classes/main/module-info.class') == 53
        classFormat('build/classes/test/MyTest.class') == 53

    }

    private static String standaloneTestClass() {
        '''
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  System.out.println(System.getProperty("java.version"));
                  Assert.assertEquals(1,1);
               }
            }

        '''
    }

    private static String java9Build() {
        '''
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                testCompile 'junit:junit:4.12'
            }

            sourceCompatibility = 1.9
            targetCompatibility = 1.9
        '''
    }

    private int classFormat(String path) {
        file(path).bytes[7] & 0xFF
    }
}
