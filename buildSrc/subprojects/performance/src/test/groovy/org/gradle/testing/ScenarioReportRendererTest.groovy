package org.gradle.testing

import groovy.util.slurpersupport.GPathResult
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject


class ScenarioReportRendererTest extends Specification {
    // set to true to develop report html and css, temp files don't get removed when debugging
    static final boolean REPORT_DEBUG = false
    @Rule
    public final TemporaryFolder tempDir = new TemporaryFolder() {
        @Override
        protected void after() {
            if (!REPORT_DEBUG) {
                super.after()
            }
        }
    }

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
        def testResultXmlFile = copyResource("TEST-sample.xml")
        def testResultFilesForBuild = [:]
        testResultFilesForBuild.put(failedBuild.@id.text(), [testResultXmlFile])

        when:
        htmlFile.withWriter { Writer writer ->
            renderer.render(writer, 'performance', [failedBuild, successfulBuild, failedBuild, successfulBuild], testResultFilesForBuild)
        }
        if (REPORT_DEBUG) {
            renderer.writeCss(htmlFile.getParentFile())
            println "Report written to ${htmlFile.toURI().toURL()}"
        }

        then:
        noExceptionThrown()
    }

    private GPathResult getSampleBuild(name) {
        getClass().getResourceAsStream(name).withStream { input ->
            new XmlSlurper().parse(input)
        }
    }

    private File copyResource(name) {
        def file = File.createTempFile("resource", ".xml", tempDir.getRoot())
        getClass().getResourceAsStream(name).withStream { input ->
            file.withOutputStream { it << input }
        }
        file
    }
}
