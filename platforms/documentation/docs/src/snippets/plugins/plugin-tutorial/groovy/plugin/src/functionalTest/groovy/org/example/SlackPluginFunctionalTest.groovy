package org.example

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

class SlackPluginFunctionalTest extends Specification {
    @TempDir
    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    def "can run task"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id('org.example.slack')
}
slack {
    token.set(System.getenv("SLACK_TOKEN"))
    channel.set('#social')
    message.set('Hello from Gradle!')
}
"""

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.output.contains("Slack message sent successfully")
    }
}