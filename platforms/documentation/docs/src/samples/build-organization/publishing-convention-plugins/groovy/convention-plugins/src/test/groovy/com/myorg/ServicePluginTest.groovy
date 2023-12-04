package com.myorg


import org.gradle.testkit.runner.TaskOutcome

class ServicePluginTest extends PluginTest {

    def setup() {
        buildFile << """
            plugins {
                id 'com.myorg.service-conventions'
            }
        """
    }

    def "integrationTest and readmeCheck tasks run with check task"() {
        given:
        new File(testProjectDir, 'README.md') << """
## Service API

        """

        when:
        def result = runTask('check')

        then:
        result.task(":test").outcome == TaskOutcome.NO_SOURCE
        result.task(":integrationTest").outcome == TaskOutcome.NO_SOURCE
        result.task(":readmeCheck").outcome == TaskOutcome.SUCCESS
    }

    def "can use integrationTest configuration to define dependencies"() {
        given:
        buildFile << """
            dependencies {
                integrationTestImplementation 'junit:junit:4.13'
            }
        """

        new File(testProjectDir, 'src/integrationTest/java/com/myorg').mkdirs()
        new File(testProjectDir, 'src/integrationTest/java/com/myorg/SomeIntegrationTest.java') << """
            package com.myorg;

            import org.junit.Test;

            public class SomeIntegrationTest {
                @Test
                public void sampleTest() {
                }
            }
        """

        when:
        def result = runTask('integrationTest')

        then:
        result.task(":integrationTest").outcome == TaskOutcome.SUCCESS
    }

    def "fails when no README exists"() {
        when:
        def result = runTaskWithFailure('check')

        then:
        result.task(":readmeCheck").outcome == TaskOutcome.FAILED
    }

    def "fails when README does not have service API section"() {
        given:
        new File(testProjectDir, 'README.md') << """
asdfadfsasf
        """

        when:
        def result = runTaskWithFailure('check')

        then:
        result.task(":readmeCheck").outcome == TaskOutcome.FAILED
        result.output.contains('README should contain section: ^## Service API$')
    }
}
