package org.gradle.testing.cucumberjvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Timeout

class CucumberJVMReportTest extends AbstractIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-2739")
    def testReportingSupportsCucumberStepsWithSlashes() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
               testCompile "junit:junit:4.11"
               testCompile "info.cukes:cucumber-java:1.1.2"
               testCompile "info.cukes:cucumber-junit:1.1.2"
            }
            test {
               testLogging.showStandardStreams = true
               testLogging.events  'started', 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
               reports.junitXml.enabled = false
               reports.html.enabled = false
            }
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }
}
