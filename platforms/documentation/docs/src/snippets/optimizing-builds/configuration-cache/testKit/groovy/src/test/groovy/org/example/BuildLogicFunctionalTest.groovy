package org.example

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    @TempDir File testProjectDir
    File buildFile

    def setup() {
        new File(testProjectDir, 'settings.gradle') << ""
        buildFile = new File(testProjectDir, 'build.gradle')
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
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
    }
}
