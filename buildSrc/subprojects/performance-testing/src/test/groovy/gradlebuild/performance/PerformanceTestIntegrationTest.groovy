package gradlebuild.performance

class PerformanceTestIntegrationTest extends AbstractIntegrationTest {
    def "honors branch name in channel"() {
        buildFile << """
            plugins {
                id 'java-library'
                id 'gradlebuild.module-identity'
                id 'gradlebuild.dependency-modules'
                id 'org.gradle.test-retry'
            }
            ext {
                libraries = ['junit5Vintage': [coordinates: 'org.junit.vintage:junit-vintage-engine', version: '5.7.0']]
            }
            subprojects {
                apply plugin: 'java'
            }
            apply plugin: 'gradlebuild.performance-test'

            def distributedPerformanceTests = tasks.withType(gradlebuild.performance.tasks.DistributedPerformanceTest)
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
            include 'internal-integ-testing', 'internal-performance-testing', 'docs', 'launcher', 'api-metadata', 'distributions-full'
        """
        expect:
        build("assertChannel")
    }
}
