package org.myorg

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class UrlVerifierPluginFunctionalTest extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            plugins {
                id 'org.myorg.url-verifier'
            }
        """
    }

    def "can successfully configure URL through extension and verify it"() {
        buildFile << """
            verification {
                url = 'https://www.google.com/'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('verifyUrl')
            .withPluginClasspath()
            .build()

        then:
        result.output.contains("Successfully resolved URL 'https://www.google.com/'")
        result.task(":verifyUrl").outcome == SUCCESS
    }
}
