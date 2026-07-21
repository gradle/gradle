package org.gradle.sample

import org.gradle.testkit.runner.ConfigurationCacheOutcome
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class BuildLogicFunctionalTest extends Specification {

    @TempDir File testProjectDir
    File buildFile

    def setup() {
        new File(testProjectDir, 'settings.gradle') << ""
        buildFile = new File(testProjectDir, 'build.gradle')
    }

    // tag::functional-test-configuration-cache-outcome[]
    def "myTask's configuration cache entry is stored and then reused"() {
        given:
        buildFile << """
            plugins {
                id 'org.gradle.sample.my-plugin'
            }
        """

        when:
        def result = runner()
            .withArguments('--configuration-cache', 'myTask')       // <1>
            .build()

        then:
        result.configurationCacheOutcome == ConfigurationCacheOutcome.STORED

        when:
        result = runner()
            .withArguments('--configuration-cache', 'myTask')       // <2>
            .build()

        then:
        result.configurationCacheOutcome == ConfigurationCacheOutcome.REUSED
    }
    // end::functional-test-configuration-cache-outcome[]

    def runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
    }
}
