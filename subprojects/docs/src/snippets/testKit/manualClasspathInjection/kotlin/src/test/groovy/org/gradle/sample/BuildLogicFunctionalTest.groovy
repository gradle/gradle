package org.gradle.sample

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.*

class BuildLogicFunctionalTest extends Specification {

    @TempDir final File testProjectDir
    File settingsFile
    File buildFile
    // tag::functional-test-classpath-setup[]
    // tag::functional-test-classpath-setup-older-gradle[]
    List<File> pluginClasspath

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
    }
    // end::functional-test-classpath-setup-older-gradle[]

    def "hello world task prints hello world"() {
        given:
        buildFile << """
            plugins {
                id 'org.gradle.sample.helloworld'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('helloWorld')
            .withPluginClasspath(pluginClasspath)
            .build()

        then:
        result.output.contains('Hello world!')
        result.task(":helloWorld").outcome == SUCCESS
    }
    // end::functional-test-classpath-setup[]
    // tag::functional-test-classpath-setup-older-gradle[]

    def "hello world task prints hello world with pre Gradle 2.8"() {
        given:
        def classpathString = pluginClasspath
            .collect { it.absolutePath.replace('\\', '\\\\') } // escape backslashes in Windows paths
            .collect { "'$it'" }
            .join(", ")

        buildFile << """
            buildscript {
                dependencies {
                    classpath files($classpathString)
                }
            }
            apply plugin: "org.gradle.sample.helloworld"
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('helloWorld')
            .withGradleVersion("2.7")
            .build()

        then:
        result.output.contains('Hello world!')
        result.task(":helloWorld").outcome == SUCCESS
    }
    // end::functional-test-classpath-setup-older-gradle[]
}
