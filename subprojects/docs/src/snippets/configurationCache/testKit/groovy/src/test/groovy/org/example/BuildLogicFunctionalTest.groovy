package org.example

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        testProjectDir.newFile('settings.gradle') << ""
        buildFile = testProjectDir.newFile('build.gradle')
    }

    // tag::functional-test-configuration-cache[]
    def "my task can be loaded from the configuration cache"() {
        given:
        buildFile << """
            plugins {
                id 'org.example.my-plugin'
            }
        """

        when:
        runner()
            .withArguments('--configuration-cache', 'myTask')    // <1>
            .build()

        and:
        def result = runner()
            .withArguments('--configuration-cache', 'myTask')    // <2>
            .build()

        then:
        result.output.contains('Reusing configuration cache.')      // <3>
        // ... more assertions on your task behavior
    }
    // end::functional-test-configuration-cache[]

    def runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
    }
}
