/*
 * Copyright 2017 the original author or authors.
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

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GFileUtils
import org.gradle.util.ToBeImplemented
import spock.lang.Issue

class TestSuiteIntegrationTest extends AbstractIntegrationSpec {

    private final static int TEST_CLASS_COUNT = 5

    def "executes JUnit-based tests defined in a suite only once when run with multiple forks"() {
        given:
        buildFile << basicJavaProject()
        buildFile << """
            dependencies {
                testImplementation 'junit:junit:4.12'
            }
            
            test {
                include '**/JUnitTestSuite.class'
            }
        """

        createTestClasses('org.junit.Test')

        file('src/test/java/JUnitTestSuite.java') << """
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;
            
            @RunWith(Suite.class)
            @Suite.SuiteClasses({
                MyTest1.class
            })
            public class JUnitTestSuite {}
        """

        when:
        succeeds('test')

        then:
        assertTestInvocations('JUnitTestSuite > MyTest1.aTest STARTED', 1)
        assertTestInvocations('JUnitTestSuite > MyTest1.bTest STARTED', 1)
        assertNoOtherTestInvocation()
    }

    @ToBeImplemented
    @Issue("https://github.com/gradle/gradle/issues/2783")
    def "executes TestNG-based tests defined in a suite only once when run with multiple forks"() {
        given:
        buildFile << basicJavaProject()
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:6.9.13.6'
            }
            
            test {
                useTestNG()
                options.suites file('testng.xml')
            }
        """

        file('testng.xml') << """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
            <suite name="unit tests" verbose="1" time-out="0">
               <test name="simple">
                  <classes>
                     <class name="MyTest1" />
                  </classes>
               </test>
            </suite>
        """

        createTestClasses('org.testng.annotations.Test')

        when:
        succeeds('test')

        then:
        assertTestInvocations('unit tests > simple > MyTest1.aTest STARTED', 2) // should be 1
        assertTestInvocations('unit tests > simple > MyTest1.bTest STARTED', 2) // should be 1
        assertNoOtherTestInvocation()
    }

    static String basicJavaProject() {
        """
            apply plugin: 'java'
            
            repositories {
                ${jcenterRepository()}
            }

            test {
                maxParallelForks = 2

                test {
                    testLogging {
                        events 'started'
                    }
                }
            }
        """
    }

    private void createTestClasses(String testAnnotation) {
        (1..TEST_CLASS_COUNT).each {
            String testClassDefinition = """
                import ${testAnnotation};
                
                public class MyTest${it} {
                    @Test
                    public void aTest() {}
                    
                    @Test
                    public void bTest() {}
                }
            """
            GFileUtils.writeFile(testClassDefinition, file("src/test/java/MyTest${it}.java"))
        }
    }

    private void assertTestInvocations(String expectedOutput, int count) {
        assert StringUtils.countMatches(output, expectedOutput) == count
    }

    private void assertNoOtherTestInvocation() {
        (2..TEST_CLASS_COUNT).each {
            assert StringUtils.countMatches(output, "MyTest${it}.aTest STARTED") == 0
            assert StringUtils.countMatches(output, "MyTest${it}.bTest STARTED") == 0
        }
    }
}
