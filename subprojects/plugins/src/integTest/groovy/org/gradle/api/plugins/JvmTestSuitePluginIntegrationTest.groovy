/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

class JvmTestSuitePluginIntegrationTest extends AbstractIntegrationSpec {

    def "JVM Test Suites plugin adds outgoing variants for default test suite"() {
        buildFile << """
plugins {
    id 'jvm-test-suite'
    id 'java'
}
"""

        file("src/test/java/SomeTest.java") << """
import org.junit.Test;

public class SomeTest {
    @Test public void foo() {}
}
"""

        expect:
        succeeds "outgoingVariants"

        outputContains("""
--------------------------------------------------
Variant testDataElementsForTest
--------------------------------------------------
Capabilities
    - :${getTestDirectory().getName()}:unspecified (default capability)
Attributes
    - org.gradle.category      = documentation
    - org.gradle.docstype      = test-results-bin
    - org.gradle.targetname    = test
    - org.gradle.testsuitename = test
    - org.gradle.testsuitetype = unit-tests
    - org.gradle.usage         = verification

Artifacts
    - build/test-results/test/binary/results.bin (artifactType = binary)
""")
    }

    def "JVM Test Suites plugin outgoing variants for custom test suite"() {
        buildFile << """
plugins {
    id 'jvm-test-suite'
    id 'java'
}

testing {
    suites {
        integrationTest(JvmTestSuite) {
            testType = TestType.INTEGRATION_TESTS

            dependencies {
                implementation project
            }
        }
    }
}
"""

        file("src/integrationTest/java/SomeTest.java") << """
import org.junit.Test;

public class SomeTest {
    @Test public void foo() {}
}
"""

        expect:
        succeeds "outgoingVariants"

        outputContains("""
--------------------------------------------------
Variant testDataElementsForIntegrationTest
--------------------------------------------------
Capabilities
    - :${getTestDirectory().getName()}:unspecified (default capability)
Attributes
    - org.gradle.category      = documentation
    - org.gradle.docstype      = test-results-bin
    - org.gradle.targetname    = integrationTest
    - org.gradle.testsuitename = integrationTest
    - org.gradle.testsuitetype = integration-tests
    - org.gradle.usage         = verification

Artifacts
    - build/test-results/integrationTest/binary/results.bin (artifactType = binary)
""")
    }

    /*
    def "Jacoco coverage data can be consumed by another task via Dependency Management"() {
        buildFile << """
test {
    jacoco {
        destinationFile = layout.buildDirectory.file("jacoco/jacocoData.exec").get().asFile
    }
}
"""

        buildFile << """
// A resolvable configuration to collect JaCoCo coverage data
def coverageDataConfig = configurations.create("coverageData") {
    visible = true
    canBeResolved = true
    canBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JACOCO_COVERAGE))
    }
}

dependencies {
    coverageData project
}

def testResolve = tasks.register('testResolve') {
    doLast {
        assert coverageDataConfig.getResolvedConfiguration().getFiles()*.getName() == ["jacocoData.exec"]
    }
}

"""
        expect:
        succeeds('test', 'testResolve')
    }

    def "Jacoco coverage data can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        final JavaProjectUnderTest subA = new JavaProjectUnderTest(subADir)
        subA.writeBuildScript()
        def buildFileA = subADir.file("build.gradle") << """
test {
    jacoco {
        destinationFile = layout.buildDirectory.file("jacoco/subA.exec").get().asFile
    }
}
"""

        def subBDir = createDir("subB")
        final JavaProjectUnderTest subB = new JavaProjectUnderTest(subBDir)
        subB.writeBuildScript()
        def buildFileB = subBDir.file("build.gradle") << """
test {
    jacoco {
        destinationFile = layout.buildDirectory.file("jacoco/subB.exec").get().asFile
    }
}
"""

        settingsFile << """
include ':subA'
include ':subB'
"""

        buildFile << """
dependencies {
    implementation project(':subA')
    implementation project(':subB')
}

// A resolvable configuration to collect JaCoCo coverage data
def coverageDataConfig = configurations.create("coverageData") {
    visible = false
    canBeResolved = true
    canBeConsumed = false
    extendsFrom(configurations.implementation)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JACOCO_COVERAGE))
    }
}

def testResolve = tasks.register('testResolve') {
    doLast {
        assert coverageDataConfig.getResolvedConfiguration().getFiles()*.getName() == ["subA.exec", "subB.exec"]
    }
}

"""
        expect:
        succeeds('testResolve')
    }

 */
}
