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

import static org.gradle.api.reporting.plugins.BuildDashboardPlugin.BUILD_DASHBOARD_TASK_NAME

class BuildDashboardPluginIntegrationTest extends WellBehavedPluginTest {

    void setup() {
        writeBuildFile()
        writeCodenarcConfig()
    }

    private void goodCode(TestFile root = testDirectory) {
        root.file("src/main/groovy/org/gradle/Class1.groovy") << "package org.gradle; class Class1 { }"
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
        BUILD_DASHBOARD_TASK_NAME
    }

    void 'running buildDashboard task on its own generates a link to it in the dashboard'() {
        when:
        run(BUILD_DASHBOARD_TASK_NAME)

        then:
        dashboardLinksCount == 1
    }

    void 'running buildDashboard task after some report generating task generates link to it in the dashboard'() {
        given:
        goodCode()

        when:
        run('check', BUILD_DASHBOARD_TASK_NAME)

        then:
        dashboardLinksCount == 4
        links.find { it.contains("':test' (html)") }
        links.find { it.contains("':test' (junitXml)") }
    }

    void 'buildDashboard task always runs after report generating tasks'() {
        given:
        goodCode()

        when:
        run(BUILD_DASHBOARD_TASK_NAME, 'check')

        then:
        dashboardLinksCount == 4
        links.find { it.contains("':test' (html)") }
        links.find { it.contains("':test' (junitXml)") }
    }

    void 'running a report generating task also runs build dashboard task'() {
        given:
        goodCode()

        when:
        run('check')

        then:
        dashboardLinksCount == 2
    }

    void 'build dashboard task is executed even if report generating task fails'() {
        given:
        badCode()

        when:
        runAndFail('check')

        then:
        dashboardLinksCount == 2
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
        runAndFail('check')

        then:
        !buildDashboardFile.exists()
    }

    void 'no report is generated if it is disabled'() {
        given:
        goodCode()
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
        given:
        goodCode()

        expect:
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in skippedTasks

        when:
        buildDashboardFile.delete()

        then:
        run(BUILD_DASHBOARD_TASK_NAME) && ":$BUILD_DASHBOARD_TASK_NAME".toString() in nonSkippedTasks
    }

    void 'enabling an additional report renders buildDashboard out-of-date'() {
        given:
        goodCode()

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
        dashboardLinksCount == 5
    }

    void 'reports from subprojects are aggregated'() {
        given:
        goodCode()
        setupSubproject()

        when:
        run(BUILD_DASHBOARD_TASK_NAME, 'check')

        then:
        dashboardLinksCount == 7
    }

    void 'dashboard lists jacoco reports'() {
        given:
        writeJavaSources()
        buildFile << """
        apply plugin:'jacoco'

        dependencies{
            testCompile "junit:junit:4.11"
        }
        """

        when:
        run("test", "jacocoTestReport")
        then:
        dashboardLinksCount == 5
        jacocoLinks() == 1

    }

    private int jacocoLinks() {
        Jsoup.parse(buildDashboardFile, null).select("ul li a:contains(':jacocoTestReport')").size()
    }

    private void writeJavaSources() {
        file("src/main/java/org/gradle/test/SimpleJava.java").createFile().text = """
    package org.gradle.test;

    public class SimpleJava {
        public void sayhello(){
            System.out.println("hello");
        }
    }"""

        file("src/test/java/org/gradle/test/SimpleJavaTest.java").createFile().text = """
            package org.gradle.test;
            import org.junit.Test;

            public class SimpleJavaTest {
                @Test public void sayhello(){
                    new SimpleJava().sayhello();
                }
            }"""
    }

    private List<String> linksLazy
    List<String> getLinks() {
        if (linksLazy == null) {
            linksLazy = dashboard.select("div#content li a")*.text()
        }
        linksLazy
    }

    Document getDashboard() {
        Jsoup.parse(buildDashboardFile, "utf8")
    }

}
