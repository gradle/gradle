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
package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

class FindBugsPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "check"
    }

    def setup() {
        writeBuildFile()
    }

    def "analyze good code"() {
        goodCode()
        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    void "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':findbugsMain'.")
        failure.assertThatCause(startsWith("FindBugs rule violations were found. See the report at:"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))
    }

    void "can ignore failures"() {
        badCode()
        buildFile << """
            findbugs {
                ignoreFailures = true
            }
        """

        expect:
        succeeds("check")
        output.contains("FindBugs rule violations were found. See the report at:")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.BadClassTest"))
    }

    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("findbugsMain") && ":findbugsMain" in nonSkippedTasks
        succeeds(":findbugsMain") && ":findbugsMain" in skippedTasks

        when:
        file("build/reports/findbugs/main.xml").delete()

        then:
        succeeds("findbugsMain") && ":findbugsMain" in nonSkippedTasks
    }

    def "cannot generate multiple reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled true
                html.enabled true
            }
        """

        and:
        goodCode()

        expect:
        fails "findbugsMain"

        failure.assertHasCause "FindBugs tasks can only have one report enabled"
    }

    def "can use optional arguments"() {
        given:
        buildFile << """
            findbugs {
                effort 'max'
                reportLevel 'high'
                includeFilter file('include.xml')
                excludeFilter file('exclude.xml')
                visitors = ['FindDeadLocalStores', 'UnreadFields']
                omitVisitors = ['WaitInLoop', 'UnnecessaryMath']
            }
            findbugsMain.reports {
                xml.enabled true
            }
        """

        and:
        goodCode()
        badCode()

        and:
        writeFilterFile('include.xml', '.*')
        writeFilterFile('exclude.xml', 'org\\.gradle\\.Bad.*')

        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    def "can generate html reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
                html.enabled true
            }
        """

        and:
        goodCode()

        when:
        run "findbugsMain"

        then:
        file("build/reports/findbugs/main.html").exists()
    }

    def "can generate no reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
                html.enabled false
            }
        """

        and:
        goodCode()

        expect:
        succeeds "findbugsMain"

        and:
        !file("build/reports/findbugs/main.html").exists()
        !file("build/reports/findbugs/main.xml").exists()
    }

    def "can analyze a lot of classes"() {
        goodCode(800)
        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class800"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class800Test"))
    }

    private goodCode(int numberOfClasses = 1) {
        1.upto(numberOfClasses) {
            file("src/main/java/org/gradle/Class${it}.java") << "package org.gradle; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/org/gradle/Class${it}Test.java") << "package org.gradle; public class Class${it}Test { public boolean isFoo(Object arg) { return true; } }"
        }
    }

    private badCode() {
        // Has DM_EXIT
        file('src/main/java/org/gradle/BadClass.java') << 'package org.gradle; public class BadClass { public boolean isFoo(Object arg) { System.exit(1); return true; } }'
        // Has ES_COMPARING_PARAMETER_STRING_WITH_EQ
        file('src/test/java/org/gradle/BadClassTest.java') << 'package org.gradle; public class BadClassTest { public boolean isFoo(Object arg) { return "true" == "false"; } }'
    }

    private Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private void writeBuildFile() {
        file("build.gradle") << """
            apply plugin: "java"
            apply plugin: "findbugs"

            repositories {
                mavenCentral()
            }
        """
    }

    private void writeFilterFile(String filename, String className) {
        file(filename) << """
            <FindBugsFilter>
            <Match>
                <Class name="${className}" />
            </Match>
            </FindBugsFilter>
        """
    }
}
