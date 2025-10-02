package org.example

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class PublishingGuardPluginTest extends Specification {

    @TempDir
    File testProjectDir

    private void write(File file, String text) {
        file.parentFile.mkdirs()
        file.text = text.stripIndent()
    }

    private GradleRunner runner(String... args) {
        GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(args)
                .withPluginClasspath()
                .forwardOutput()
    }

    private void createBuild(String version) {
        write(new File(testProjectDir, 'settings.gradle'), """
            rootProject.name = 'test-project'
        """)

        write(new File(testProjectDir, 'build.gradle'), """
            plugins { id 'org.example.pubcheck' }
            version = '$version'
        """)
    }

    private File problemsReport() {
        new File(testProjectDir, 'build/reports/problems/problems-report.html')
    }

    def "version 1 fails the build (throws)"() {
        given:
        createBuild('1')

        when:
        BuildResult result = runner('pubcheck').buildAndFail()
        String out = result.output

        then:
        out.contains("Version '1' not allowed")
        problemsReport().exists()
    }

    def "version 2 reports a warning but build succeeds"() {
        given:
        createBuild('2')

        when:
        BuildResult result = runner('pubcheck').build()
        String out = result.output

        then:
        problemsReport().exists()
    }

    def "other versions pass silently"() {
        given:
        createBuild('3')

        when:
        BuildResult result = runner('pubcheck').build()
        String out = result.output

        then:
        !problemsReport().exists()
    }
}
