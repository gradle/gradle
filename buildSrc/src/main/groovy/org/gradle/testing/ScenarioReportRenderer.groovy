package org.gradle.testing

import groovy.util.slurpersupport.NodeChildren
import groovy.xml.MarkupBuilder

class ScenarioReportRenderer {
    void render(String projectName, List<Object> finishedBuilds, Writer writer) {
        def markup = new MarkupBuilder(writer)

        markup.html {
            head {
                title "Performance test scenarios report"
                meta("http-equiv": "Content-Type", content: "text/html; charset=utf-8")
                link(rel: "stylesheet", type: "text/css", href: "scenario-report-style.css")
            }
            def buildsSuccessOrNot = finishedBuilds.groupBy { build -> build.@status.toString() == 'SUCCESS' }
            def successfullBuilds = buildsSuccessOrNot.get(true)
            def otherBuilds = buildsSuccessOrNot.get(false)
            if (otherBuilds) {
                h3 'Unsuccessful builds'
                renderResultTable(markup, projectName, otherBuilds, true)
            }
            if (successfullBuilds) {
                h3 'Successful builds'
                renderResultTable(markup, projectName, successfullBuilds)
            }
        }
    }

    private renderResultTable(markup, projectName, builds, showExtraColumns = false) {
        def closure = {
            table {
                thead {
                    tr {
                        th("Scenario")
                        th("Status")
                        if (showExtraColumns) {
                            th("")
                            th("")
                        }
                        th("")
                        th("")
                    }
                }
                builds.each { build ->
                    tr(class: build.@status.toString().toLowerCase()) {
                        td(this.getScenarioId(build))
                        td(build.@status)
                        if (showExtraColumns) {
                            td(build.statusText.text())
                        }
                        td {
                            a(href: build.@webUrl, "Test Report")
                        }
                        if (showExtraColumns) {
                            td {
	                       a(href: "https://builds.gradle.org/repository/download/${build.@buildTypeId}/${build.@id}:id/reports/${projectName}/fullPerformanceTest/index.html", "Test Summary")
                            }
                        }
                        td {
                            a(href: "report/tests/${this.getScenarioId(build).replaceAll(" ", "-")}.html", "Performance Report")
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
        properties.find { it.@name == 'scenario' }.@value
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
