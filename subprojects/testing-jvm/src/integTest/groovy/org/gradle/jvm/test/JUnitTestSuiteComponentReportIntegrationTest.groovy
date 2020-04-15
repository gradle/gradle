/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution

@UnsupportedWithInstantExecution(because = "software model")
class JUnitTestSuiteComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
                id 'junit-test-suite'
                id 'java-lang'
            }

            ${jcenterRepository()}
        """
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The java-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The junit-test-suite plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    def "shows details of standalone Junit test suite"() {
        given:
        buildFile << '''
        model {
            testSuites {
                test(JUnitTestSuiteSpec) {
                    jUnitVersion = 4.12
                }
            }
        }
        '''

        when:
        succeeds "components"

        then:
        outputMatches """
JUnit test suite 'test'
-----------------------

Source sets
    Java source 'test:java'
        srcDir: src/test/java
    JVM resources 'test:resources'
        srcDir: src/test/resources

Binaries
    Test suite 'test:binary'
        build using task: :testBinary
        run using task: :testBinaryTest
        target platform: $currentJava
        JUnit version: 4.12
        tool chain: $currentJdk
        classes dir: build/classes/test/binary
        resources dir: build/resources/test/binary
"""
    }

    def "shows details of multiple standalone Junit test suites"() {
        given:
        buildFile << """
model {
    testSuites {
        unitTest(JUnitTestSuiteSpec) {
            jUnitVersion = 4.12
        }
        functionalTest(JUnitTestSuiteSpec) {
            jUnitVersion = 4.12
        }
    }
}
"""

        when:
        succeeds "components"

        then:
        outputMatches """
JUnit test suite 'functionalTest'
---------------------------------

Source sets
    Java source 'functionalTest:java'
        srcDir: src/functionalTest/java
    JVM resources 'functionalTest:resources'
        srcDir: src/functionalTest/resources

Binaries
    Test suite 'functionalTest:binary'
        build using task: :functionalTestBinary
        run using task: :functionalTestBinaryTest
        target platform: $currentJava
        JUnit version: 4.12
        tool chain: $currentJdk
        classes dir: build/classes/functionalTest/binary
        resources dir: build/resources/functionalTest/binary

JUnit test suite 'unitTest'
---------------------------

Source sets
    Java source 'unitTest:java'
        srcDir: src/unitTest/java
    JVM resources 'unitTest:resources'
        srcDir: src/unitTest/resources

Binaries
    Test suite 'unitTest:binary'
        build using task: :unitTestBinary
        run using task: :unitTestBinaryTest
        target platform: $currentJava
        JUnit version: 4.12
        tool chain: $currentJdk
        classes dir: build/classes/unitTest/binary
        resources dir: build/resources/unitTest/binary
"""
    }

    def "shows details of test suite with component under test"() {
        given:
        buildFile << '''
        model {
            components {
                main(JvmLibrarySpec)
            }
            testSuites {
                unitTest(JUnitTestSuiteSpec) {
                    jUnitVersion = 4.12
                    testing $.components.main
                }
            }
        }
        '''

        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'main'
------------------

Source sets
    Java source 'main:java'
        srcDir: src/main/java
    JVM resources 'main:resources'
        srcDir: src/main/resources

Binaries
    Jar 'main:jar'
        build using task: :mainJar
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/main/jar
        resources dir: build/resources/main/jar
        API Jar file: build/jars/main/jar/api/main.jar
        Jar file: build/jars/main/jar/main.jar

JUnit test suite 'unitTest'
---------------------------

Source sets
    Java source 'unitTest:java'
        srcDir: src/unitTest/java
    JVM resources 'unitTest:resources'
        srcDir: src/unitTest/resources

Binaries
    Test suite 'unitTest:mainJarBinary'
        build using task: :unitTestMainJarBinary
        run using task: :unitTestMainJarBinaryTest
        target platform: $currentJava
        JUnit version: 4.12
        component under test: JVM library 'main'
        binary under test: Jar 'main:jar'
        tool chain: $currentJdk
        classes dir: build/classes/unitTest/mainJarBinary
        resources dir: build/resources/unitTest/mainJarBinary
"""
    }

    def "shows details of test suite with component under test with multiple variants"() {
        given:
        buildFile << '''
        model {
            components {
                main(JvmLibrarySpec) {
                    targetPlatform 'java6'
                    targetPlatform 'java7'
                }
            }
            testSuites {
                unitTest(JUnitTestSuiteSpec) {
                    jUnitVersion = 4.12
                    testing $.components.main
                }
            }
        }
        '''

        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'main'
------------------

Source sets
    Java source 'main:java'
        srcDir: src/main/java
    JVM resources 'main:resources'
        srcDir: src/main/resources

Binaries
    Jar 'main:java6Jar'
        build using task: :mainJava6Jar
        target platform: Java SE 6
        tool chain: $currentJdk
        classes dir: build/classes/main/java6Jar
        resources dir: build/resources/main/java6Jar
        API Jar file: build/jars/main/java6Jar/api/main.jar
        Jar file: build/jars/main/java6Jar/main.jar
    Jar 'main:java7Jar'
        build using task: :mainJava7Jar
        target platform: Java SE 7
        tool chain: $currentJdk
        classes dir: build/classes/main/java7Jar
        resources dir: build/resources/main/java7Jar
        API Jar file: build/jars/main/java7Jar/api/main.jar
        Jar file: build/jars/main/java7Jar/main.jar

JUnit test suite 'unitTest'
---------------------------

Source sets
    Java source 'unitTest:java'
        srcDir: src/unitTest/java
    JVM resources 'unitTest:resources'
        srcDir: src/unitTest/resources

Binaries
    Test suite 'unitTest:mainJava6JarBinary'
        build using task: :unitTestMainJava6JarBinary
        run using task: :unitTestMainJava6JarBinaryTest
        target platform: Java SE 6
        JUnit version: 4.12
        component under test: JVM library 'main'
        binary under test: Jar 'main:java6Jar'
        tool chain: $currentJdk
        classes dir: build/classes/unitTest/mainJava6JarBinary
        resources dir: build/resources/unitTest/mainJava6JarBinary
    Test suite 'unitTest:mainJava7JarBinary'
        build using task: :unitTestMainJava7JarBinary
        run using task: :unitTestMainJava7JarBinaryTest
        target platform: Java SE 7
        JUnit version: 4.12
        component under test: JVM library 'main'
        binary under test: Jar 'main:java7Jar'
        tool chain: $currentJdk
        classes dir: build/classes/unitTest/mainJava7JarBinary
        resources dir: build/resources/unitTest/mainJava7JarBinary
"""
    }

}
