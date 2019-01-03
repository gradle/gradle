package org.gradle.testing

import groovy.util.slurpersupport.NodeChildren
import groovy.xml.MarkupBuilder
import org.openmbee.junit.model.JUnitTestSuite

class ScenarioReportRenderer {
    void render(Writer writer, String projectName, List<Object> finishedBuilds, Map<String, List<JUnitTestSuite>> testResultsForBuild) {
        def markup = new MarkupBuilder(writer)

        markup.html {
            head {
                title "Performance test scenarios report"
                meta("http-equiv": "Content-Type", content: "text/html; charset=utf-8")
                link(rel: "stylesheet", type: "text/css", href: "scenario-report-style.css")
            }
            def buildsSuccessOrNot = finishedBuilds.sort(false) { build -> getScenarioId(build)?:'' }.groupBy { build -> build.@status.toString() == 'SUCCESS' }
            def successfulBuilds = buildsSuccessOrNot.get(true)
            def otherBuilds = buildsSuccessOrNot.get(false)
            if (otherBuilds) {
                h3 "${otherBuilds.size()} Failed scenarios"
                renderResultTable(markup, projectName, otherBuilds, testResultsForBuild, true)
            }
            if (successfulBuilds) {
                h3 "${successfulBuilds.size()} Successful scenarios"
                renderResultTable(markup, projectName, successfulBuilds, testResultsForBuild)
            }
        }
    }

    private renderResultTable(markup, projectName, builds, Map<String, List<JUnitTestSuite>> testResultsForBuild, failed = false) {
        def closure = {
            table {
                thead {
                    tr {
                        th("Scenario")
                        th("")
                        if (failed) {
                            th("")
                        }
                        th("")
                    }
                }
                builds.eachWithIndex { build, idx ->
                    def rowClass = build.@status.toString().toLowerCase()
                    def testResults = testResultsForBuild.get(build.@id.text())
                    tr(class: rowClass) {
                        td("${idx+1}. ${this.getScenarioId(build)}")
                        td {
                            a(href: build.@webUrl, "Go to this Build")
                        }
                        if (failed) {
                            td {
                                a(href: "https://builds.gradle.org/repository/download/${build.@buildTypeId}/${build.@id}:id/reports/${projectName}/fullPerformanceTest/index.html", "Test Summary")
                            }
                        }
                        td {
                            a(href: "report/tests/${this.getScenarioId(build).replaceAll(" ", "-")}.html", "Performance Report")
                        }
                    }
                    if (failed) {
                        testResults?.each { testSuite ->
                            testSuite?.testCases?.each { testCase ->
                                testCase?.failures?.each { failure ->
                                    tr(class: rowClass) {
                                        td(colspan: 4) {
                                            span(class: 'code') {
                                                pre(failure.value)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        closure.delegate = markup
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }


    private String getScenarioId(Object build) {
        NodeChildren properties = build.properties.children()
        properties.find { it.@name == 'scenario' }.@value.text()
    }

    void writeCss(File directory) {
        def cssFile = new File(directory, "scenario-report-style.css")
        def input = getClass().getResourceAsStream("scenario-report-style.css")
        input.withStream {
            cssFile.withOutputStream { out ->
                out << input
            }
        }
    }
}
