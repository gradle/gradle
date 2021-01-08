package com.myorg

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class PluginTest extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle') << "rootProject.name = 'test'"
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def runTask(String task) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .build()
    }

    def runTaskWithFailure(String task) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()
    }
}
