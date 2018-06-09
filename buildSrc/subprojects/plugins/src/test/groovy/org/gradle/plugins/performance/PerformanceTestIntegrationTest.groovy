package org.gradle.plugins.performance

class PerformanceTestIntegrationTest extends AbstractIntegrationTest {
    def "honors branch name in channel"() {
        buildFile << """
            plugins {
                id 'gradlebuild.performance-test'
            }
            
            def distributedPerformanceTests = tasks.withType(org.gradle.testing.DistributedPerformanceTest)
            distributedPerformanceTests.all {
                // resolve these tasks
            }
            task assertChannel {
                doLast {
                    distributedPerformanceTests.each { distributedPerformanceTest ->
                        assert distributedPerformanceTest.channel.endsWith("-branch")
                    }
                }
            }
        """

        file("internalPerformanceTesting/build.gradle") << "apply plugin: 'java'"
        settingsFile << """
            include 'internalPerformanceTesting'
        """
        expect:
        build("assertChannel", "-Porg.gradle.performance.branchName=branch")
    }
}
