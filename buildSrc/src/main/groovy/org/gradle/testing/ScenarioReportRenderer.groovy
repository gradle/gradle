package org.gradle.testing

import groovy.util.slurpersupport.NodeChildren
import groovy.xml.MarkupBuilder

class ScenarioReportRenderer {
    void render(List<Object> finishedBuilds, Writer writer) {
        def markup = new MarkupBuilder(writer)

        markup.html{
            table {
                thead {
                    tr {
                        th("Scenario")
                        th("Status")
                        th("")
                        th("")
                    }
                }
                finishedBuilds.each { build ->
                    tr {
                        td(this.getScenarioId(build))
                        td(build.@status)
                        td {
                            a(href : build.@webUrl, "Test Report")
                        }
                        td {
                            a(href : "report/tests/${this.getScenarioId(build).replaceAll(" ", "-")}.html", "Performance Report")
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
}
