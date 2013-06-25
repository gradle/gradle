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
import org.jsoup.nodes.Document

class BuildDashboardPluginIntegrationTest extends WellBehavedPluginTest {

    void setup() {
        writeBuildFile()
        writeCodenarcConfig()
    }

    private void goodCode(TestFile root = testDirectory) {
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
    }

    private void goodTests(TestFile root = testDirectory) {
        root.file("src/test/groovy/org/gradle/Class1.groovy") << "package org.gradle; class TestClass1 { @org.junit.Test void ok() { } }"
        buildFile << """
            dependencies{
                testCompile "junit:junit:4.11"
            }
"""
    }

    private void badCode(TestFile root = testDirectory) {
        root.file("src/main/groovy/org/gradle/class1.groovy") << "package org.gradle; class class1 { }"
    }

    private void writeCodenarcConfig(TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
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

    private void failingDependenciesForCodenarcTasks() {
        buildFile << """
            task failingTask << { throw new RuntimeException() }

            codenarcMain.dependsOn failingTask
            codenarcTest.dependsOn failingTask
        """
    }

    private void setupSubproject() {
        def subprojectDir = file('subproject')
        writeCodenarcConfig(subprojectDir)
        goodCode(subprojectDir)
        file('settings.gradle') << "include 'subproject'"
    }

    String getPluginId() {
        'build-dashboard'
    }

    String getMainTask() {
        'buildDashboard'
    }

    void 'running buildDashboard task on its own generates a link to it in the dashboard'() {
        when:
        run('buildDashboard')

        then:
        dashboardLinksCount == 1
        hasReport(':buildDashboard', 'html')
    }

    void 'running buildDashboard task after some report generating task generates link to it in the dashboard'() {
        given:
        goodCode()
        goodTests()

        when:
        run('check', 'buildDashboard')

        then:
        dashboardLinksCount == 5
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
        hasReport(':codenarcTest', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'buildDashboard task always runs after report generating tasks'() {
        given:
        goodCode()
        goodTests()

        when:
        run('buildDashboard', 'check')

        then:
        dashboardLinksCount == 5
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
        hasReport(':codenarcTest', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'running a report generating task also runs build dashboard task'() {
        given:
        goodCode()

        when:
        run('check')

        then:
        dashboardLinksCount == 2
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
    }

    void 'build dashboard task is executed even if report generating task fails'() {
        given:
        badCode()

        when:
        runAndFail('check')

        then:
        dashboardLinksCount == 2
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
    }

    void 'build dashboard task is not executed if a dependency of the report generating task fails'() {
        given:
        goodCode()
        failingDependenciesForCodenarcTasks()

        when:
        runAndFail('check')

        then:
        !buildDashboardFile.exists()
    }

    void 'build dashboard task is not executed if a dependency of the report generating task fails even with --continue'() {
        given:
        goodCode()
        failingDependenciesForCodenarcTasks()

        when:
        args('--continue')
        runAndFail('codeNarcMain')

        then:
        !buildDashboardFile.exists()
    }

    void 'no report is generated if it is disabled'() {
        given:
        goodCode()
        buildFile << """
            buildDashboard {
                reports.html.enabled = false
            }
        """

        when:
        run('buildDashboard')

        then:
        !buildDashboardFile.exists()
    }

    void 'buildDashboard is incremental'() {
        given:
        goodCode()

        expect:
        run('buildDashboard') && ':buildDashboard' in nonSkippedTasks
        run('buildDashboard') && ':buildDashboard' in skippedTasks

        when:
        buildDashboardFile.delete()

        then:
        run('buildDashboard') && ':buildDashboard' in nonSkippedTasks
    }

    void 'enabling an additional report renders buildDashboard out-of-date'() {
        given:
        goodCode()

        when:
        run('check', 'buildDashboard') && ':buildDashboard' in nonSkippedTasks

        then:
        dashboardLinksCount == 2
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')

        when:
        buildFile << """
            codenarcMain {
                reports.text.enabled = true
            }
        """

        and:
        run('check', 'buildDashboard') && ':buildDashboard' in nonSkippedTasks

        then:
        dashboardLinksCount == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
        hasReport(':codenarcMain', 'text')
    }

    void 'reports from subprojects are aggregated'() {
        given:
        goodCode()
        setupSubproject()

        when:
        run('buildDashboard', 'check')

        then:
        dashboardLinksCount == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
        hasReport(':subproject:codenarcMain', 'html')
    }

    void 'dashboard lists JaCoCo reports'() {
        given:
        goodTests()
        buildFile << """
            apply plugin:'jacoco'
        """

        when:
        run("test", "jacocoTestReport")

        then:
        dashboardLinksCount == 4
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
        hasReport(':jacocoTestReport', 'html')
    }

    void hasReport(String task, String name) {
        assert links.contains("Report generated by task '$task' ($name)" as String)
    }

    List<String> getLinks() {
        dashboard.select("div#content li a")*.text()
    }

    List<String> getUnavailable() {
        dashboard.select("div#content li span.unavailable")*.text()
    }

    private Document doc
    private boolean attached

    Document getDashboard() {
        if (doc == null) {
            doc = Jsoup.parse(buildDashboardFile, "utf8")
        }
        if (!attached) {
            executer.beforeExecute { doc = null }
            attached = true
        }
        return doc
    }

}
