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

class JUnitTestSuiteComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    def "shows details of stand alone Junit test suite"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'junit-test-suite'
    id 'java-lang'
}

repositories {
    jcenter()
}

model {
    components {
        test(JUnitTestSuiteSpec) {
            JUnitVersion = 4.12
        }
    }
}
"""

        when:
        succeeds "components"

        then:
        outputMatches output, """
JUnitTestSuiteSpec 'test'
-------------------------

Source sets
    Java source 'test:java'
        srcDir: src/test/java
    JVM resources 'test:resources'
        srcDir: src/test/resources

Binaries
    Test 'test:binary'
        build using task: :testBinary
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/test/binary
        resources dir: build/resources/test/binary
"""
    }

    def "shows details of multiple stand alone Junit test suites"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'junit-test-suite'
    id 'java-lang'
}

repositories {
    jcenter()
}

model {
    components {
        unitTest(JUnitTestSuiteSpec) {
            JUnitVersion = 4.12
        }
        functionalTest(JUnitTestSuiteSpec) {
            JUnitVersion = 4.12
        }
    }
}
"""

        when:
        succeeds "components"

        then:
        outputMatches output, """
JUnitTestSuiteSpec 'functionalTest'
-----------------------------------

Source sets
    Java source 'functionalTest:java'
        srcDir: src/functionalTest/java
    JVM resources 'functionalTest:resources'
        srcDir: src/functionalTest/resources

Binaries
    Test 'functionalTest:binary'
        build using task: :functionalTestBinary
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/functionalTest/binary
        resources dir: build/resources/functionalTest/binary

JUnitTestSuiteSpec 'unitTest'
-----------------------------

Source sets
    Java source 'unitTest:java'
        srcDir: src/unitTest/java
    JVM resources 'unitTest:resources'
        srcDir: src/unitTest/resources

Binaries
    Test 'unitTest:binary'
        build using task: :unitTestBinary
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/unitTest/binary
        resources dir: build/resources/unitTest/binary
"""
    }
}
