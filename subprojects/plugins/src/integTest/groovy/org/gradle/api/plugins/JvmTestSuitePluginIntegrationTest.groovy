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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile

class JvmTestSuitePluginIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {

    def "JVM Test Suites plugin adds outgoing variants for default test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'java'
            }
            """

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsPath = new TestFile(getTestDirectory(), 'build/test-results/test/binary').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsPath (artifactType = directory)""".stripIndent())

        and:
        hasIncubatingLegend()
    }

    def "JVM Test Suites plugin adds outgoing variants for custom test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'java'
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST

                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsPath = new TestFile(getTestDirectory(), 'build/test-results/integrationTest/binary').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant testResultsElementsForIntegrationTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the integrationTest Test Suite's integrationTest target.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = integrationTest
    - org.gradle.testsuite.target.name = integrationTest
    - org.gradle.testsuite.type        = integration-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsPath (artifactType = directory)""".stripIndent())

        and:
        hasIncubatingLegend()
    }

    def "Test coverage data can be consumed by another task via Dependency Management"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        buildFile << """
            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create('testData') {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            dependencies {
                testData project
            }

            // Extract the artifact view for cc compatibility by providing it as an explicit task input
            def artifactView = testDataConfig.incoming.artifactView { view ->
                                   view.componentFilter { it in ProjectComponentIdentifier }
                                   view.lenient = true
                               }

            def testResolve = tasks.register('testResolve') {
                inputs.files(artifactView.files)
                doLast {
                    assert inputs.files.singleFile.name == 'binary'
                }
            }""".stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "Test results data can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subADir.file("src/test/java/SomeTestA.java") << """
            import org.junit.Test;

            public class SomeTestA {
                @Test public void foo() {}
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subBDir.file("src/test/java/SomeTestB.java") << """
            import org.junit.Test;

            public class SomeTestB {
                @Test public void foo() {}
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':subA')
                implementation project(':subB')
            }

            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            // Extract the artifact view for cc compatibility by providing it as an explicit task input
            def artifactView = testDataConfig.incoming.artifactView { view ->
                                   view.componentFilter { it in ProjectComponentIdentifier }
                                   view.lenient = true
                               }


            def testResolve = tasks.register('testResolve') {
                inputs.files(artifactView.files)
                def expectedResultsFiles = [file("subA/build/test-results/test/binary"),
                                            file("subB/build/test-results/test/binary")]
                doLast {
                    assert inputs.files.files.containsAll(expectedResultsFiles)
                }
            }

            """
        expect:
        succeeds('testResolve')
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "Test results data can be consumed across transitive project dependencies via Dependency Management"() {
        def subDirectDir = createDir("direct")
        subDirectDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                implementation project(':transitive')
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subDirectDir.file("src/test/java/SomeTestD.java") << """
            import org.junit.Test;

            public class SomeTestD {
                @Test public void foo() {}
            }
            """.stripIndent()

        def subTransitiveDir = createDir("transitive")
        subTransitiveDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subTransitiveDir.file("src/test/java/SomeTestT.java") << """
            import org.junit.Test;

            public class SomeTestT {
                @Test public void foo() {}
            }
            """.stripIndent()

        settingsFile << """
            include ':direct'
            include ':transitive'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':direct')
            }

            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    def artifactView = testDataConfig.incoming.artifactView { view ->
                        view.componentFilter { it in ProjectComponentIdentifier }
                        view.lenient = true
                    }.files
                    assert artifactView.files.containsAll([project(':direct').tasks["test"].binaryResultsDirectory.get().asFile,
                                                           project(':transitive').tasks["test"].binaryResultsDirectory.get().asFile])
                }
            }

            """
        expect:
        succeeds('testResolve')
    }

    def "Only one suite with a given test type allowed per project"() {
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """plugins {
                id 'java'
            }

            testing {
                suites {
                    primaryIntTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                    }

                    secondaryIntTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                    }
                }
            }
            """.stripIndent()

        expect:
        fails('primaryIntTest', 'secondaryIntTest')
        result.assertHasErrorOutput("Could not configure suite: 'secondaryIntTest'. Another test suite: 'primaryIntTest' uses the type: 'integration-test' and has already been configured in project: 'Test'.")
    }

    def "Only one suite with a given test type allowed per project (including the built-in test suite)"() {
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """plugins {
                id 'java'
            }

            testing {
                suites {
                    secondaryTest(JvmTestSuite) {
                        testType = TestSuiteType.UNIT_TEST
                    }
                }
            }
            """.stripIndent()

        expect:
        fails('test', 'secondaryTest')
        result.assertHasErrorOutput("Could not configure suite: 'test'. Another test suite: 'secondaryTest' uses the type: 'unit-test' and has already been configured in project: 'Test'.")
    }

    def "Only one suite with a given test type allowed per project (using the default type of one suite and explicitly setting the other)"() {
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """plugins {
                id 'java'
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite)

                    secondaryIntegrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                    }
                }
            }
            """.stripIndent()

        expect:
        fails('integrationTest', 'secondaryIntegrationTest')
        result.assertHasErrorOutput("Could not configure suite: 'secondaryIntegrationTest'. Another test suite: 'integrationTest' uses the type: 'integration-test' and has already been configured in project: 'Test'.")
    }

    def "Test suites in different projects can use same test type"() {
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                    }
                }
            }""".stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                    }
                }
            }""".stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            tasks.register('allIntegrationTests') {
                dependsOn(':subA:integrationTest', ':subB:integrationTest')
            }
            """.stripIndent()

        expect:
        succeeds('allIntegrationTests')
    }

    private String systemFilePath(String path) {
        return path.replace('/', File.separator)
    }
}
