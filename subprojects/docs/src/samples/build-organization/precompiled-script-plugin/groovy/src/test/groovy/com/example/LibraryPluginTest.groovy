package com.example

import org.gradle.testkit.runner.TaskOutcome

class LibraryPluginTest extends PluginTest {

    def setup() {
        buildFile << """
            plugins {
                id 'com.example.library'
            }
        """
    }

    def "can declare api dependencies"() {
        given:
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

        testProjectDir.newFolder('src', 'main', 'java', 'com', 'example')
        testProjectDir.newFile('src/main/java/com/example/Util.java') << """
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
        new File(testProjectDir.getRoot(), 'build/test-repo/com/example/my-library/0.1.0/my-library-0.1.0.jar').exists()
    }
}
