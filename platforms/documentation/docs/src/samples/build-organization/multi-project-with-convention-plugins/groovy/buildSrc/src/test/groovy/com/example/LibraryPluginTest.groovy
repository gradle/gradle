package com.example

import org.gradle.testkit.runner.TaskOutcome

class LibraryPluginTest extends PluginTest {

    def setup() {
        buildFile << """
            plugins {
                id 'myproject.library-conventions'
            }
        """
    }

    def "can declare api dependencies"() {
        given:
        readmeContainingMandatorySectionsExists()
        buildFile << """
            dependencies {
                api 'org.apache.commons:commons-lang3:3.4'
            }
        """

        when:
        def result = runTask('build')

        then:
        result.task(":build").outcome == TaskOutcome.SUCCESS
    }

    def "publishes library with versionin"() {
        given:
        readmeContainingMandatorySectionsExists()
        settingsFile.setText("rootProject.name = 'my-library'")
        buildFile << """
            version = '0.1.0'

            publishing {
                repositories {
                    maven {
                        name 'testRepo'
                        url 'build/test-repo'
                    }
                }
            }
        """

        new File(testProjectDir, 'src/main/java/com/example').mkdirs()
        new File(testProjectDir, 'src/main/java/com/example/Util.java') << """
            package com.example;

            public class Util {
                public static void someUtil() {
                }
            }
        """

        when:
        def result = runTask('publishLibraryPublicationToTestRepoRepository')

        then:
        result.task(":jar").outcome == TaskOutcome.SUCCESS
        result.task(":publishLibraryPublicationToTestRepoRepository").outcome == TaskOutcome.SUCCESS
        new File(testProjectDir, 'build/test-repo/com/example/my-library/0.1.0/my-library-0.1.0.jar').exists()
    }

    def "fails when no README exists"() {
        when:
        def result = runTaskWithFailure('check')

        then:
        result.task(":readmeCheck").outcome == TaskOutcome.FAILED
    }

    def "fails when README does not have API section"() {
        given:
        new File(testProjectDir, 'README.md') << """
## Changelog
- change 1
- change 2
        """

        when:
        def result = runTaskWithFailure('check')

        then:
        result.task(":readmeCheck").outcome == TaskOutcome.FAILED
        result.output.contains('README should contain section: ^## API$')
    }

    def "fails when README does not have Changelog section"() {
        given:
        new File(testProjectDir, 'README.md') << """
## API
public API description
        """

        when:
        def result = runTaskWithFailure('check')

        then:
        result.task(":readmeCheck").outcome == TaskOutcome.FAILED
        result.output.contains('README should contain section: ^## Changelog')
    }

    private def readmeContainingMandatorySectionsExists() {
        new File(testProjectDir, 'README.md') << """
## API
public API description

## Changelog
- change 1
- change 2
        """
    }
}
