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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoPluginIntegrationTest extends AbstractIntegrationSpec {

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
        errorOutput.contains("JaCoCo destination file must not be null if output type is FILE")
    }

    def "jacoco plugin adds outgoing variants for default test suite"() {
        expect:
        succeeds "outgoingVariants"

        outputContains("""
            --------------------------------------------------
            Variant coverageDataElementsForTest
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category      = documentation
                - org.gradle.docstype      = jacoco-coverage-bin
                - org.gradle.targetname    = test
                - org.gradle.testsuitename = test
                - org.gradle.testsuitetype = unit-tests
                - org.gradle.usage         = verification

            Artifacts
                - build${File.separator}jacoco${File.separator}test.exec (artifactType = binary)""".stripIndent())
    }

    def "jacoco plugin adds outgoing variants for custom test suite"() {
        buildFile << """
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
        """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        outputContains("""
            --------------------------------------------------
            Variant coverageDataElementsForIntegrationTest
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category      = documentation
                - org.gradle.docstype      = jacoco-coverage-bin
                - org.gradle.targetname    = integrationTest
                - org.gradle.testsuitename = integrationTest
                - org.gradle.testsuitetype = integration-tests
                - org.gradle.usage         = verification

            Artifacts
                - build${File.separator}jacoco${File.separator}integrationTest.exec (artifactType = binary)""".stripIndent())
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
                    assert coverageDataConfig.getResolvedConfiguration().getFiles()*.getName() == [test.jacoco.destinationFile.name]
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
        def buildFileA = subADir.file("build.gradle") << """
            test {
                jacoco {
                    destinationFile = layout.buildDirectory.file("jacoco/subA.exec").get().asFile
                }
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        final JavaProjectUnderTest subB = new JavaProjectUnderTest(subBDir)
        subB.writeBuildScript()
        def buildFileB = subBDir.file("build.gradle") << """
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
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }
}
