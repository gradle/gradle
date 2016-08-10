package org.gradle.testing

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
        def buildData = getClass().getResourceAsStream("sample-build-result.xml").withStream { input ->
            new XmlSlurper().parse(input)
        }

        when:
        htmlFile.withWriter { Writer writer ->
            renderer.render("performance", [buildData], writer)
        }

        then:
        noExceptionThrown()
    }
}
