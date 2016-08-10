package org.gradle.testing

import groovy.util.slurpersupport.NodeChildren
import groovy.xml.MarkupBuilder

class ScenarioReportRenderer {
    void render(List<Object> finishedBuilds, Writer writer) {
        def markup = new MarkupBuilder(writer)

        markup.html {
            head {
                title "Performance test scenarios report"
                meta("http-equiv": "Content-Type", content: "text/html; charset=utf-8")
                link(rel: "stylesheet", type: "text/css", href: "scenario-report-style.css")
            }
            table {
                thead {
                    tr {
                        th("Scenario")
                        th("Status")
                        th("")
                        th("")
                        th("")
                        th("")
                    }
                }
                finishedBuilds.each { build ->
                    tr(class: build.@status.toString().toLowerCase()) {
                        td(this.getScenarioId(build))
                        td(build.@status)
                        td(build.statusText.text())
                        td {
                            a(href: build.@webUrl, "Test Report")
                        }
                        td {
                            a(href: "https://builds.gradle.org/repository/download/${build.@buildTypeId}/${build.@id}:id/reports/performance/performanceScenario/index.html", "Test Summary")
                        }
                        td {
                            a(href: "report/tests/${this.getScenarioId(build).replaceAll(" ", "-")}.html", "Performance Report")
                        }
                    }
                }
            }
        }
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
