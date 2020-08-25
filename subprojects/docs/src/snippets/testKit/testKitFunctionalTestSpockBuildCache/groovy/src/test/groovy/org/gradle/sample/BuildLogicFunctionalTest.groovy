package org.gradle.sample

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    // tag::clean-build-cache[]
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File localBuildCacheDirectory

    def setup() {
        localBuildCacheDirectory = testProjectDir.newFolder('local-cache')
        testProjectDir.newFile('settings.gradle') << """
            buildCache {
                local {
                    directory '${localBuildCacheDirectory.toURI()}'
                }
            }
        """
        buildFile = testProjectDir.newFile('build.gradle')
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
        new File(testProjectDir.root, 'build').deleteDir()
        result = runner()
            .withArguments( '--build-cache', 'cacheableTask')
            .build()

        then:
        result.task(":cacheableTask").outcome == FROM_CACHE
    }
    // end::functional-test-build-cache[]

    def runner() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
    }
}
