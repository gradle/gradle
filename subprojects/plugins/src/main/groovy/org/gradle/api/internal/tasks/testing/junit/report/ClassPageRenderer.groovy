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

class ClassPageRenderer extends PageRenderer<ClassTestResults> {

    @Override protected void renderBreadcrumbs() {
        builder.div(class: 'breadcrumbs') {
            a(href: 'index.html', 'all')
            mkp.yield(" > ")
            a(href: "${results.packageResults.name}.html", results.packageResults.name)
            mkp.yield(" > ${results.simpleName}")
        }
    }

    @Override protected void registerTabs() {
        if (results.failures) {
            addTab('Failed tests') {
                for (test in results.failures) {
                    builder.div(class: 'test') {
                        a(name: test.id, " ")
                        h3(class: test.statusClass, test.name)
                        for (failure in test.failures) {
                            pre(class: 'stackTrace', failure.stackTrace)
                        }
                    }
                }
            }
        }
        addTab('Tests') {
            builder.table {
                thead {
                    tr {
                        th('Test')
                        th('Duration')
                        th('Result')
                    }
                }
                for (test in results.testResults) {
                    tr {
                        a(name: test.id, " ")
                        td(class: test.statusClass, test.name)
                        td(test.formattedDuration)
                        td(class: test.statusClass, test.formattedResultType)
                    }
                }
            }
        }
        if (results.standardOutput.length() > 0) {
            addTab('Standard output') {
                builder.pre(results.standardOutput)
            }
        }
        if (results.standardError.length() > 0) {
            addTab('Standard error') {
                builder.pre(results.standardError)
            }
        }
    }
}
