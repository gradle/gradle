package com.example

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildLogicFunctionalTest extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "greet task prints hello world"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            plugins {
                id 'com.example.my-plugin'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('greet')
            .withPluginClasspath()
            .build()

        then:
        result.output.contains('Hello, World!')
        result.task(":greet").outcome == TaskOutcome.SUCCESS
    }
}
