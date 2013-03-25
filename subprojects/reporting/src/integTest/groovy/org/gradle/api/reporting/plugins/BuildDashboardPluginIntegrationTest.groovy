/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.reporting.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.file.TestFile
import org.jsoup.Jsoup

import static org.gradle.api.reporting.plugins.BuildDashboardPlugin.BUILD_DASHBOARD_TASK_NAME

class BuildDashboardPluginIntegrationTest extends WellBehavedPluginTest {

    void setup() {
        writeBuildFile()
        writeProjectFiles(testDirectory)
    }

    private void writeProjectFiles(TestFile root) {
        root.file("src/main/groovy/org/gradle/class1.groovy") << "package org.gradle; class class1 { }"
        root.file("config/codenarc/rulesets.groovy") << ""
    }

    private TestFile getBuildDashboardFile() {
        file("build/reports/buildDashboard/index.html")
    }

    private int getDashboardLinksCount() {
        Jsoup.parse(buildDashboardFile, null).select('ul li a').size()
    }

    private void writeBuildFile() {
        buildFile << """
            apply plugin: 'build-dashboard'

            allprojects {
                apply plugin: 'groovy'
                apply plugin: 'codenarc'

                codenarc {
                    configFile = file('config/codenarc/rulesets.groovy')
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    codenarc 'org.codenarc:CodeNarc:0.16.1'
                    compile localGroovy()
                }
            }
        """
    }

    private void setupSubproject() {
        writeProjectFiles(file('subproject'))
        file('settings.gradle') << "include 'subproject'"
    }

    String getPluginId() {
        'build-dashboard'
    }

    String getMainTask() {
        BUILD_DASHBOARD_TASK_NAME
    }

    void 'running buildDashboard task on its own generates a link to it in the dashboard'() {
        when:
        run(BUILD_DASHBOARD_TASK_NAME)

        then:
        dashboardLinksCount == 1
    }

    void 'running buildDashboard task after some report generating task generates link to it in the dashboard'() {
        when:
        run('check', BUILD_DASHBOARD_TASK_NAME)

        then:
        dashboardLinksCount == 2
    }

    void 'buildDashboard task always runs after report generating tasks'() {
        when:
        run(BUILD_DASHBOARD_TASK_NAME, 'check')

        then:
        dashboardLinksCount == 2
    }

    void 'no report is generated if it is disabled'() {
        given:
        buildFile << """
            $BUILD_DASHBOARD_TASK_NAME {
                reports.html.enabled = false
            }
        """

        when:
        run(BUILD_DASHBOARD_TASK_NAME)

        then:
        !buildDashboardFile.exists()
    }

    void 'buildDashboard is incremental'() {
        expect:
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in skippedTasks

        when:
        buildDashboardFile.delete()

        then:
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks
    }

    void 'enabling an additional report renders buildDashboard out-of-date'() {
        expect:
        run('check', BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks

        when:
        buildFile << """
            codenarcMain {
                reports.text.enabled = true
            }
        """

        then:
        run('check', BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks
        dashboardLinksCount == 3
    }

    void 'reports from subprojects are aggregated'() {
        given:
        setupSubproject()

        when:
        run(BUILD_DASHBOARD_TASK_NAME, 'check')

        then:
        dashboardLinksCount == 3
    }
}
