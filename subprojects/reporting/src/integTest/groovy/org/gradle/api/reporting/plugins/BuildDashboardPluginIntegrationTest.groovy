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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.IgnoreIf

class BuildDashboardPluginIntegrationTest extends WellBehavedPluginTest {

    def setup() {
        writeBuildFile()
    }

    private void goodCode(TestFile root = testDirectory) {
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
        buildFile << """
            allprojects {
                apply plugin: 'groovy'

                dependencies {
                    compile localGroovy()
                }
            }
        """
    }

    private void withTests() {
        buildFile << """
            allprojects {
                dependencies{
                    testCompile "junit:junit:4.12"
                }
            }
"""
    }

    private void goodTests(TestFile root = testDirectory) {
        root.file("src/test/groovy/org/gradle/Class1.groovy") << "package org.gradle; class TestClass1 { @org.junit.Test void ok() { } }"
        withTests()
    }

    private void badTests(TestFile root = testDirectory) {
        root.file("src/test/groovy/org/gradle/Class1.groovy") << "package org.gradle; class TestClass1 { @org.junit.Test void broken() { throw new RuntimeException() } }"
        withTests()
    }

    private void withCodenarc(TestFile root = testDirectory) {
        root.file("config/codenarc/rulesets.groovy") << """
            ruleset {
                ruleset('rulesets/naming.xml')
            }
        """
        buildFile << """
            allprojects {
                apply plugin: 'codenarc'

                codenarc {
                    configFile = file('config/codenarc/rulesets.groovy')
                }
            }
"""
    }

    private void writeBuildFile() {
        buildFile << """
            apply plugin: 'build-dashboard'

            allprojects {
                ${mavenCentralRepository()}
            }
        """
    }

    private void failingDependenciesForTestTask() {
        buildFile << """
            task failingTask { doLast { throw new RuntimeException() } }

            test.dependsOn failingTask
        """
    }

    private void setupSubproject() {
        def subprojectDir = file('subproject')
        goodCode(subprojectDir)
        goodTests(subprojectDir)
        file('settings.gradle') << "include 'subproject'"
    }

    String getMainTask() {
        'buildDashboard'
    }

    void 'build dashboard for a project with no other reports lists just the dashboard'() {
        when:
        run('buildDashboard')

        then:
        reports.size() == 1
        hasReport(':buildDashboard', 'html')
        unavailableReports.empty
    }

    void 'build dashboard lists the enabled reports for the project'() {
        given:
        goodCode()
        goodTests()

        when:
        run('check', 'buildDashboard')

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'build dashboard lists the reports which have not been generated'() {
        given:
        goodCode()
        goodTests()

        when:
        run('buildDashboard')

        then:
        reports.size() == 1
        hasReport(':buildDashboard', 'html')
        unavailableReports.size() == 2
        hasUnavailableReport(':test', 'html')
        hasUnavailableReport(':test', 'junitXml')
    }

    void 'build dashboard is always generated after report generating tasks have executed'() {
        given:
        goodCode()
        goodTests()

        when:
        run('buildDashboard', 'check')

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'running a report generating task also generates build dashboard'() {
        given:
        goodCode()
        goodTests()

        when:
        run('test')

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'build dashboard is generated even if report generating task fails'() {
        given:
        goodCode()
        badTests()

        when:
        runAndFail('check')

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
    }

    void 'build dashboard is not generated if a dependency of the report generating task fails'() {
        given:
        goodCode()
        goodTests()
        failingDependenciesForTestTask()

        when:
        runAndFail('check')

        then:
        !buildDashboardFile.exists()
    }

    void 'build dashboard is not generated if a dependency of the report generating task fails even with --continue'() {
        given:
        goodCode()
        goodTests()
        failingDependenciesForTestTask()

        when:
        args('--continue')
        runAndFail('check')

        then:
        !buildDashboardFile.exists()
    }

    void 'dashboard is not generated if it is disabled'() {
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

    @IgnoreIf({GradleContextualExecuter.parallel})
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

    @IgnoreIf({GradleContextualExecuter.parallel})
    void 'enabling an additional report renders buildDashboard out-of-date'() {
        given:
        goodCode()
        withCodenarc()

        when:
        run('check') && ':buildDashboard' in nonSkippedTasks

        then:
        reports.size() == 2
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')

        when:
        buildFile << """
            codenarcMain {
                reports.text.enabled = true
            }
        """

        and:
        run('check') && ':buildDashboard' in nonSkippedTasks

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
        hasReport(':codenarcMain', 'text')
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    void 'generating a report that was previously not available renders buildDashboard out-of-date'() {
        given:
        goodCode()
        goodTests()

        when:
        run('buildDashboard') && ':buildDashboard' in nonSkippedTasks

        then:
        reports.size() == 1
        hasReport(':buildDashboard', 'html')
        unavailableReports.size() == 2
        hasUnavailableReport(':test', 'html')
        hasUnavailableReport(':test', 'junitXml')

        when:
        run('test') && ':buildDashboard' in nonSkippedTasks

        then:
        reports.size() == 3
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
        unavailableReports.empty
    }

    void 'reports from subprojects are aggregated'() {
        given:
        goodCode()
        goodTests()
        setupSubproject()

        when:
        run('buildDashboard', 'check')

        then:
        reports.size() == 5
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
        hasReport(':subproject:test', 'html')
        hasReport(':subproject:test', 'junitXml')
    }

    @Requires(TestPrecondition.FIX_TO_WORK_ON_JAVA9)
    void 'dashboard includes JaCoCo reports'() {
        given:
        goodCode()
        goodTests()
        buildFile << """
            apply plugin:'jacoco'
        """

        when:
        run("test", "jacocoTestReport")

        then:
        reports.size() == 4
        hasReport(':buildDashboard', 'html')
        hasReport(':test', 'html')
        hasReport(':test', 'junitXml')
        hasReport(':jacocoTestReport', 'html')
    }

    void 'dashboard includes CodeNarc reports'() {
        given:
        goodCode()
        withCodenarc()

        when:
        run("check")

        then:
        reports.size() == 2
        hasReport(':buildDashboard', 'html')
        hasReport(':codenarcMain', 'html')
    }

    void hasReport(String task, String name) {
        assert reports.contains("Report generated by task '$task' ($name)" as String)
    }

    List<String> getReports() {
        dashboard.select("div#content li a")*.text()
    }

    void hasUnavailableReport(String task, String name) {
        assert unavailableReports.contains("Report generated by task '$task' ($name)" as String)
    }

    List<String> getUnavailableReports() {
        dashboard.select("div#content li span.unavailable")*.text()
    }

    private TestFile getBuildDashboardFile() {
        file("build/reports/buildDashboard/index.html")
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
