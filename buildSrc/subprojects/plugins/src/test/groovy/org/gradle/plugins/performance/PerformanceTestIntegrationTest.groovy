package org.gradle.plugins.performance

class PerformanceTestIntegrationTest extends AbstractIntegrationTest {
    def "honors branch name in channel"() {
        buildFile << """
            plugins {
                id 'java-library'
                id 'gradlebuild.build-version'
                id 'gradlebuild.performance-test'
            }
            subprojects {
                apply plugin: 'java'
            }

            apply plugin: 'gradlebuild.performance-test'

            def distributedPerformanceTests = tasks.withType(org.gradle.testing.DistributedPerformanceTest)
            distributedPerformanceTests.all {
                // resolve these tasks
            }
            task assertChannel {
                doLast {
                    distributedPerformanceTests.each { distributedPerformanceTest ->
                        assert distributedPerformanceTest.channel.endsWith("-myBranch")
                    }
                }
            }
        """

        file("version.txt") << '6.5'
        settingsFile << """
            include 'internalPerformanceTesting', 'docs', 'launcher', 'apiMetadata'
        """
        expect:
        build("assertChannel")
    }
}
