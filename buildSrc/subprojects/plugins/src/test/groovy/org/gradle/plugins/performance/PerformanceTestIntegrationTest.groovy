package org.gradle.plugins.performance

class PerformanceTestIntegrationTest extends AbstractIntegrationTest {
    def "honors branch name in channel"() {
        buildFile << """
            plugins {
                id 'java-library'
                id 'gradlebuild.build-version'
            }
            ext {
                libraries = ['junit5_vintage': [coordinates: 'org.junit.vintage:junit-vintage-engine', version: '5.6.2']]
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
            include 'internalIntegTesting', 'internalPerformanceTesting', 'docs', 'launcher', 'apiMetadata', 'distributionsFull'
        """
        expect:
        build("assertChannel")
    }
}
