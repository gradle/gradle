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

class OverviewPageRenderer extends PageRenderer<AllTestResults> {

    @Override protected void renderBreadcrumbs() {
    }

    @Override protected void registerTabs() {
        addFailuresTab()
        if (!results.packages.empty) {
            addTab("Packages") {
                builder.table {
                    thead {
                        tr {
                            th('Package')
                            th('Tests')
                            th('Failures')
                            th('Duration')
                            th('Success rate')
                        }
                    }
                    for (testPackage in results.packages) {
                        tr {
                            td(class: testPackage.statusClass) {
                                a(href: "${testPackage.name}.html", testPackage.name)
                            }
                            td(testPackage.testCount)
                            td(testPackage.failureCount)
                            td(testPackage.formattedDuration)
                            td(class: testPackage.statusClass, testPackage.formattedSuccessRate)
                        }
                    }
                }
            }
            addTab("Classes") {
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
                    for (testPackage in results.packages) {
                        for (testClass in testPackage.classes) {
                            tr {
                                td(class: testClass.statusClass) {
                                    a(href: "${testClass.name}.html", testClass.name)
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
    }
}
