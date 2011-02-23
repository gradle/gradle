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

class PackagePageRenderer extends PageRenderer<PackageTestResults> {

    @Override protected void renderBreadcrumbs() {
        builder.div(class: 'breadcrumbs') {
            a(href: 'index.html', 'all')
            mkp.yield(" > ${results.name}")
        }
    }

    @Override protected void registerTabs() {
        addFailuresTab()
        addTab('Classes') {
            builder.table {
                thead {
                    tr {
                        th('Class')
                        th('Tests')
                        th('Failures')
                        th('Duration')
                        th('Success rate')
                    }
                }
                for (testClass in results.classes) {
                    tr {
                        td(class: testClass.statusClass) {
                            a(href: "${testClass.name}.html", testClass.simpleName)
                        }
                        td(testClass.testCount)
                        td(testClass.failureCount)
                        td(testClass.formattedDuration)
                        td(class: testClass.statusClass, testClass.formattedSuccessRate)
                    }
                }
            }
        }
    }
}
