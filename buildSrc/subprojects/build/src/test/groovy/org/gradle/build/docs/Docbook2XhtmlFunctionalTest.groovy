package org.gradle.build.docs

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class Docbook2XhtmlFunctionalTest extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "convert some docbook"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            import org.gradle.build.docs.Docbook2Xhtml

            task docbookHtml(type: Docbook2Xhtml) {
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('docbookHtml')
                .withPluginClasspath()
                .build()

        then:
        result.output.contains('Hello world!')
        result.task(":helloWorld").outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS
    }
}
