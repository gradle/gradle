package org.gradle.sample

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    // tag::clean-build-cache[]
    @TempDir File testProjectDir
    File buildFile
    File localBuildCacheDirectory

    def setup() {
        localBuildCacheDirectory = new File(testProjectDir, 'local-cache')
        buildFile = new File(testProjectDir,'settings.gradle') << """
            buildCache {
                local {
                    directory = '${localBuildCacheDirectory.toURI()}'
                }
            }
        """
        buildFile = new File(testProjectDir,'build.gradle')
    }
    // end::clean-build-cache[]

    // tag::functional-test-build-cache[]
    def "cacheableTask is loaded from cache"() {
        given:
        buildFile << """
            plugins {
                id 'org.gradle.sample.helloworld'
            }
        """

        when:
        def result = runner()
            .withArguments( '--build-cache', 'cacheableTask')
            .build()

        then:
        result.task(":cacheableTask").outcome == SUCCESS

        when:
        new File(testProjectDir, 'build').deleteDir()
        result = runner()
            .withArguments( '--build-cache', 'cacheableTask')
            .build()

        then:
        result.task(":cacheableTask").outcome == FROM_CACHE
    }
    // end::functional-test-build-cache[]

    def runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
    }
}
