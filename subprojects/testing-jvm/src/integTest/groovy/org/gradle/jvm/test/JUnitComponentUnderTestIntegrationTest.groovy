/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.test

class JUnitComponentUnderTestIntegrationTest extends AbstractJUnitTestExecutionIntegrationSpec {

    def "can test a JVM library"() {
        given:
        applyJUnitPlugin()
        jvmLibrary()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        succeeds ':myTestGreeterJarBinaryTest'

        then:
        executedAndNotSkipped ':compileGreeterJarGreeterJava', ':myTestGreeterJarBinaryTest'
    }

    def "reasonable error message when component under test does not exist"() {
        given:
        applyJUnitPlugin()
        myTestSuiteSpec('greeter')
        greeterTestCase()

        when:
        fails 'components'

        then:
        failure.assertHasCause "Component 'greeter' declared under test 'JUnitTestSuiteSpec 'myTest'' does not exist"
    }

    private void jvmLibrary() {
        buildFile << '''
            model {
                components {
                    greeter(JvmLibrarySpec)
                }
            }
        '''
        file('src/greeter/java/com/acme/Greeter.java') << '''package com.acme;
            public class Greeter {
                public String greet(String name) {
                    return "Hello, " + name + "!";
                }
            }
        '''
    }

    private void greeterTestCase() {
        file('src/myTest/java/com/acme/GreeterTest.java') << '''package com.acme;
            import org.junit.Test;

            import static org.junit.Assert.*;

            public class GreeterTest {
                @Test
                public void testGreeting() {
                    Greeter greeter = new Greeter();
                    assertEquals("Hello, Amanda!", greeter.greet("Amanda"));
                }
            }
        '''
    }
}
