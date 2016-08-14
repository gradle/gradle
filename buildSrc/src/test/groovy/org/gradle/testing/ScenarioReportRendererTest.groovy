package org.gradle.testing

import groovy.util.slurpersupport.GPathResult
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject


class ScenarioReportRendererTest extends Specification {
    @Rule
    public final TemporaryFolder tempDir = new TemporaryFolder()

    @Subject
    ScenarioReportRenderer renderer = new ScenarioReportRenderer()

    def "should create css file"() {
        given:
        def cssDir = tempDir.newFolder('css')

        when:
        renderer.writeCss(cssDir)

        then:
        new File(cssDir, 'scenario-report-style.css').exists()
    }

    def "should create html report"() {
        given:
        def htmlFile = tempDir.newFile("scenario-report.html")
        def failedBuild = getSampleBuild("sample-build-result-failure.xml")
        def successfulBuild = getSampleBuild("sample-build-result-success.xml")

        when:
        htmlFile.withWriter { Writer writer ->
            renderer.render('performance', [failedBuild, successfulBuild, failedBuild, successfulBuild], writer)
        }

        then:
        noExceptionThrown()
    }

    private GPathResult getSampleBuild(name) {
        getClass().getResourceAsStream(name).withStream { input ->
            new XmlSlurper().parse(input)
        }
    }
}
