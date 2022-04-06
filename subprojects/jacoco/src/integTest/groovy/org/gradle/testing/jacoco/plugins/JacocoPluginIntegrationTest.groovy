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

package org.gradle.testing.jacoco.plugins

import org.gradle.api.Project
import org.gradle.api.reporting.ReportingExtension
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoPluginIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)
    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "does not add jvmArgs if jacoco is disabled"() {
        buildFile << """
            test {
                jacoco {
                    enabled = false
                }

                doLast {
                    assert allJvmArgs.every { !it.contains("javaagent") }
                }
            }
        """
        expect:
        succeeds "test"
    }

    def "jacoco plugin adds coverage report for test task when java plugin applied"() {
        given:
        buildFile << '''
            assert project.test.extensions.getByType(JacocoTaskExtension) != null
            assert project.jacocoTestReport instanceof JacocoReport
            assert project.jacocoTestReport.sourceDirectories*.absolutePath == project.layout.files("src/main/java")*.absolutePath
            assert project.jacocoTestReport.classDirectories*.absolutePath == project.sourceSets.main.output*.absolutePath
        '''.stripIndent()

        expect:
        succeeds 'help'
    }

    @ToBeFixedForConfigurationCache(because = ":dependencies")
    def "dependencies report shows default jacoco dependencies"() {
        when:
        succeeds("dependencies", "--configuration", "jacocoAgent")
        then:
        output.contains "org.jacoco:org.jacoco.agent:"

        when:
        succeeds("dependencies", "--configuration", "jacocoAnt")
        then:
        output.contains "org.jacoco:org.jacoco.ant:"
    }

    @ToBeFixedForConfigurationCache(because = ":dependencies")
    def "allows configuring tool dependencies explicitly"() {
        when:
        buildFile << """
            dependencies {
                //downgrade version:
                jacocoAgent "org.jacoco:org.jacoco.agent:0.6.0.201210061924"
                jacocoAnt "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
            }
        """

        succeeds("dependencies", "--configuration", "jacocoAgent")
        then:
        output.contains "org.jacoco:org.jacoco.agent:0.6.0.201210061924"

        when:
        succeeds("dependencies", "--configuration", "jacocoAnt")
        then:
        output.contains "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
    }

    def "jacoco report is incremental"() {
        def reportResourceDir = file("${REPORTING_BASE}/jacoco/test/html/jacoco-resources")

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        executedAndNotSkipped(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        succeeds('jacocoTestReport')

        then:
        skipped(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        reportResourceDir.deleteDir()
        succeeds('test', 'jacocoTestReport')

        then:
        executedAndNotSkipped(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()
    }

    private JacocoReportFixture htmlReport(String basedir = "${REPORTING_BASE}/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }

    def "reports miss configuration of destination file"() {
        given:
        buildFile << """
            test {
                jacoco {
                    destinationFile = provider { null }
                }
            }
        """

        when:
        runAndFail("test")

        then:
        // TODO: This is not the message we want, but destinationFile is exposed as a File and not a provider
        errorOutput.contains("Cannot query the value of this provider because it has no value available.")
    }

    def "jacoco plugin adds outgoing variants for default test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        expect:
        succeeds "outgoingVariants"

        def resultsExecPath = new TestFile(getTestDirectory(), 'build/jacoco/test.exec').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant coverageDataElementsForTest (i)
--------------------------------------------------
Binary data file containing results of Jacoco test coverage reporting for the test Test Suite's test target.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = jacoco-coverage
Artifacts
    - $resultsExecPath (artifactType = binary)
""")

        and:
        hasIncubatingLegend()
    }

    def "jacoco plugin adds outgoing variants for custom test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
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

        def resultsExecPath = new TestFile(getTestDirectory(), 'build/jacoco/integrationTest.exec').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant coverageDataElementsForIntegrationTest (i)
--------------------------------------------------
Binary data file containing results of Jacoco test coverage reporting for the integrationTest Test Suite's integrationTest target.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = integrationTest
    - org.gradle.testsuite.target.name = integrationTest
    - org.gradle.testsuite.type        = integration-test
    - org.gradle.verificationtype      = jacoco-coverage
Artifacts
    - $resultsExecPath (artifactType = binary)""")

        and:
        hasIncubatingLegend()
    }

    def "Jacoco coverage data can be consumed by another task via Dependency Management"() {
        buildFile << """
            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/jacocoData.exec").get().asFile
                }
            }
            """.stripIndent()

        buildFile << """
            // A resolvable configuration to collect JaCoCo coverage data
            def coverageDataConfig = configurations.create("coverageData") {
                visible = true
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.JACOCO_RESULTS))
                }
            }

            dependencies {
                coverageData project
            }

            // Extract the artifact view for cc compatibility by providing it as an explicit task input
            def artifactView = coverageDataConfig.incoming.artifactView { view ->
                                   view.componentFilter { it in ProjectComponentIdentifier }
                                   view.lenient = true
                               }

            def testResolve = tasks.register('testResolve') {
                inputs.files(artifactView.files)
                doLast {
                    assert inputs.files.singleFile.name == 'jacocoData.exec'
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "Jacoco coverage data can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        final JavaProjectUnderTest subA = new JavaProjectUnderTest(subADir)
        subA.writeBuildScript()
        subADir.file("build.gradle") << """
            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/subA.exec").get().asFile
                }
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        final JavaProjectUnderTest subB = new JavaProjectUnderTest(subBDir)
        subB.writeBuildScript()
        subBDir.file("build.gradle") << """
            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/subB.exec").get().asFile
                }
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

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
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.JACOCO_RESULTS))
                }
            }

            // Extract the artifact view for cc compatibility by providing it as an explicit task input
            def artifactView = coverageDataConfig.incoming.artifactView { view ->
                                   view.componentFilter { it in ProjectComponentIdentifier }
                                   view.lenient = true
                               }

            def testResolve = tasks.register('testResolve') {
                inputs.files(artifactView.files)
                doLast {
                    assert inputs.files*.name == ["subA.exec", "subB.exec"]
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "Jacoco coverage data can be consumed across transitive project dependencies via Dependency Management"() {
        def directDir = createDir("direct")
        final JavaProjectUnderTest subDirect = new JavaProjectUnderTest(directDir)
        subDirect.writeBuildScript()
        directDir.file("build.gradle") << """
            dependencies {
                implementation project(':transitive')
            }

            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/direct.exec").get().asFile
                }
            }
            """.stripIndent()

        def transitiveDir = createDir("transitive")
        final JavaProjectUnderTest subTransitive = new JavaProjectUnderTest(transitiveDir)
        subTransitive.writeBuildScript()
        transitiveDir.file("build.gradle") << """
            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/transitive.exec").get().asFile
                }
            }
            """.stripIndent()

        settingsFile << """
            include ':direct'
            include ':transitive'
            """.stripIndent()

        buildFile << """
            dependencies {
                implementation project(':direct')
            }

            // A resolvable configuration to collect JaCoCo coverage data
            def coverageDataConfig = configurations.create("coverageData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.JACOCO_RESULTS))
                }
            }


            // Extract the artifact view for cc compatibility by providing it as an explicit task input
            def artifactView = coverageDataConfig.incoming.artifactView { view ->
                                   view.componentFilter { it in ProjectComponentIdentifier }
                                   view.lenient = true
                               }

            def testResolve = tasks.register('testResolve') {
                inputs.files(artifactView.files)
                doLast {
                    assert inputs.files*.name == ["direct.exec", "transitive.exec"]
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }
}
