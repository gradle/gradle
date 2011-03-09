/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.testing.junit.report

import groovy.xml.MarkupBuilder
import org.gradle.util.GradleVersion
import java.text.DateFormat

abstract class PageRenderer<T extends CompositeTestResults> {
    private T results
    private MarkupBuilder builder
    private List tabs = []

    protected T getResults() {
        return results
    }

    protected MarkupBuilder getBuilder() {
        return builder
    }

    protected List getTabs() {
        return tabs
    }

    protected abstract void renderBreadcrumbs()

    protected abstract void registerTabs()

    protected void addTab(String title, Closure contentRenderer) {
        tabs << [title: title, renderer: contentRenderer]
    }

    protected void renderTabs() {
        builder.div(id: 'tabs') {
            ul(class: 'tabLinks') {
                tabs.eachWithIndex { tab, index ->
                    li {
                        a(href: "#tab${index}", tab.title)
                    }
                }
            }
            tabs.eachWithIndex { tab, index ->
                div(class: "tab", id: "tab${index}") {
                    h2(tab.title)
                    tab.renderer.call()
                }
            }
        }
    }

    protected void addFailuresTab() {
        if (results.failures) {
            addTab("Failed tests") {
                builder.ul(class: 'linkList') {
                    for (test in results.failures) {
                        li {
                            a(href: "${test.classResults.name}.html", test.classResults.simpleName)
                            mkp.yield('.')
                            a(href: "${test.classResults.name}.html#${test.id}", test.name)
                        }
                    }
                }
            }
        }
    }

    void render(MarkupBuilder builder, T results) {
        this.results = results
        this.builder = builder

        registerTabs()

        builder.html {
            head {
                meta('http-equiv': "Content-Type", content: "text/html; charset=UTF-8")
                title("Test results - $results.title")
                link(rel: 'stylesheet', href: 'style.css', type: 'text/css')
                script(src: 'report.js', type: 'text/javascript', " ")
            }
            body {
                div(id: 'content') {
                    h1(results.title)
                    renderBreadcrumbs()
                    div(id: 'summary') {
                        table {
                            tr {
                                td {
                                    div(class: 'summaryGroup') {
                                        table {
                                            tr {
                                                td {
                                                    div(id: 'tests', class: 'infoBox') {
                                                        div(class: 'counter', results.testCount)
                                                        p('tests')
                                                    }
                                                }
                                                td {
                                                    div(id: 'failures', class: 'infoBox') {
                                                        div(class: 'counter', results.failureCount)
                                                        p('failures')
                                                    }
                                                }
                                                td {
                                                    div(id: 'duration', class: 'infoBox') {
                                                        div(class: 'counter', results.formattedDuration)
                                                        p('duration')
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                td {
                                    div(id: 'successRate', class: "infoBox ${results.statusClass}") {
                                        div(class: 'percent', results.formattedSuccessRate)
                                        p('successful')
                                    }
                                }
                            }
                        }
                    }
                    renderTabs()
                    div(id: 'footer') {
                        p {
                            mkp.yield('Generated by ')
                            a(href: 'http://www.gradle.org', "Gradle ${GradleVersion.current().version}")
                            mkp.yield(" at ${DateFormat.getDateTimeInstance().format(new Date())}")
                        }
                    }
                }
            }
        }
    }
}
