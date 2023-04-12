/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins.quality.pmd


import org.gradle.util.internal.VersionNumber
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not
import static org.junit.Assume.assumeTrue

class PmdPluginVersionIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id("java")
                id("pmd")
            }

            ${mavenCentralRepository()}

            testing.suites.test.useJUnit()

            pmd {
                toolVersion = '$version'
                ${supportIncrementalAnalysis() ? "" : "incrementalAnalysis = false"}
            }

            ${fileLockingIssuesSolved() ? "" : """
            tasks.withType(Pmd) {
                // clear the classpath to avoid file locking issues on PMD version < 5.5.1
                classpath = files()
            }"""}

            ${requiredSourceCompatibility()}
        """.stripIndent()
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds("check")
        file("build/reports/pmd/main.xml").exists()
        file("build/reports/pmd/test.xml").exists()
    }

    def "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'.")
        failure.assertThatCause(containsString("2 PMD rule violations were found. See the report at:"))
        failure.assertHasResolutions(SCAN)
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    void "can ignore failures"() {
        badCode()
        buildFile << """
            pmd {
                ignoreFailures = true
            }
        """

        expect:
        succeeds("check")
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
        output.contains("2 PMD rule violations were found. See the report at:")
    }

    void "can set max failures"() {
        badCode()
        buildFile << """
            pmd {
                maxFailures = 2
            }
        """

        expect:
        succeeds("check")
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
        output.contains("2 PMD rule violations were found. See the report at:")
    }

    void "does not ignore more than max failures"() {
        badCode()
        buildFile << """
            pmd {
                maxFailures = 1
            }
        """

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'.")
        failure.assertThatCause(containsString("2 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    void "can configure priority level threshold"() {
        badCode()
        buildFile << """
            pmd {
                rulesMinimumPriority = 2
            }
        """

        expect:
        fails("check")
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").
            assertContents(containsClass("org.gradle.Class1Test")).
            assertContents(containsLine(containsString('AvoidMultipleUnaryOperators'))).
            assertContents(not(containsLine(containsString('OverrideBothEqualsAndHashcode'))))
    }

    def "gets reasonable message when priority level threshold is out of range from extension"() {
        goodCode()
        buildFile << """
        pmd {
            rulesMinimumPriority = 11
        }
"""
        expect:
        // Use --continue so that when executing in parallel mode a deterministic set of tests run
        // without --continue, sometimes both pmd tasks are run and sometimes only the only one task is run
        fails("check", "--continue")
        failure.assertHasCause("Invalid rulesMinimumPriority '11'.  Valid range 1 (highest) to 5 (lowest).")
        // pmdMain and pmdTest
        failure.assertHasFailures(2)
    }

    def "gets reasonable message when priority level threshold is out of range from task"() {
        goodCode()
        buildFile << """
        pmdMain {
            rulesMinimumPriority = 11
        }
"""
        expect:
        fails("check")
        failure.assertHasCause("Invalid rulesMinimumPriority '11'.  Valid range 1 (highest) to 5 (lowest).")
    }

    def "can configure reporting"() {
        goodCode()
        buildFile << """
            pmdMain {
                reports {
                    xml.required = false
                    html.outputLocation = file("htmlReport.html")
                }
            }
        """

        expect:
        succeeds("check")
        !file("build/reports/pmd/main.xml").exists()
        file("htmlReport.html").exists()
    }

    def "use custom rule set files"() {
        assumeTrue(fileLockingIssuesSolved())

        customCode()
        customRuleSet()

        buildFile << """
            pmd {
                ruleSetFiles = files("customRuleSet.xml")
            }
        """

        expect:
        fails("pmdMain")
        failure.assertHasDescription("Execution failed for task ':pmdMain'.")
        failure.assertThatCause(containsString("1 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/main.xml").assertContents(containsClass("org.gradle.Class2"))
    }

    def "add custom rule set files"() {
        assumeTrue(fileLockingIssuesSolved())

        customCode()
        customRuleSet()

        buildFile << """
            pmd {
                ruleSetFiles = files()
                ruleSetFiles "customRuleSet.xml"
            }
        """

        expect:
        fails("pmdMain")
        failure.assertHasDescription("Execution failed for task ':pmdMain'.")
        failure.assertThatCause(containsString("1 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/main.xml").assertContents(containsClass("org.gradle.Class2"))
    }

    def "use custom rule set"() {
        customCode()

        buildFile << """
            pmd {
                ruleSetConfig = resources.text.fromString('''${customRuleSetText()}''')
            }
        """

        expect:
        fails("pmdMain")
        failure.assertHasDescription("Execution failed for task ':pmdMain'.")
        failure.assertThatCause(containsString("1 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/main.xml").assertContents(containsClass("org.gradle.Class2"))

    }

    def "can enable console output"() {
        buildFile << """
            pmd {
                consoleOutput = true
            }
        """
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'.")
        failure.assertThatCause(containsString("2 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
        output.contains "\tUsing multiple unary operators may be a bug"
        output.contains "\tEnsure you override both equals() and hashCode()"
    }

    void "can configure number of threads on good code"() {
        goodCode()
        buildFile << """
            pmd {
                threads = 2
            }
        """

        expect:
        succeeds("check")
        file("build/reports/pmd/main.xml").exists()
        file("build/reports/pmd/test.xml").exists()
    }

    def "can configure number of threads on bad code"() {
        badCode()
        buildFile << """
            pmd {
                threads = 2
            }
        """

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'.")
        failure.assertThatCause(containsString("2 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    def "gets reasonable message when number of threads is negative from extension"() {
        goodCode()
        buildFile << """
            pmd {
                threads = -1
            }
        """

        expect:
        // Use --continue so that when executing in parallel mode a deterministic set of tests run
        // without --continue, sometimes both pmd tasks are run and sometimes only the only one task is run
        fails("check", "--continue")
        failure.assertHasCause("Invalid number of threads '-1'.  Number should not be negative.")
        // pmdMain and pmdTest
        failure.assertHasFailures(2)
    }

    def "gets reasonable message when number of threads is negative from task"() {
        goodCode()
        buildFile << """
            pmdMain {
                threads = -1
            }
        """

        expect:
        fails("check")
        failure.assertHasCause("Invalid number of threads '-1'.  Number should not be negative.")
    }

    @Issue("https://github.com/gradle/gradle/issues/2326")
    def "check task should not be up-to-date after clean if it only outputs to console"() {
        given:
        badCode()
        buildFile << """
            pmd {
                consoleOutput = true
                ignoreFailures = true
            }
            tasks.withType(Pmd) {
                reports {
                    html.required = false
                    xml.required = false
                }
            }
        """

        when:
        succeeds('check')
        succeeds('clean', 'check')

        then:
        executedAndNotSkipped(':pmdMain')
        output.contains("PMD rule violations were found")
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private goodCode() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean isFoo(Object arg) { return true; } }"
    }

    private badCode() {
        // No Warnings
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        // PMD Lvl 2 Warning AvoidMultipleUnaryOperators
        // PMD Lvl 3 Warning OverrideBothEqualsAndHashcode
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean equals(Object arg) { return !!true; } }"
    }

    private customCode() {
        // class that would fail default rule set but doesn't fail custom rule set
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; public class Class1 { public void doit() { boolean x = true; if (x) {} } }" // empty then-block
        // class that wouldn't fail default rule set but does fail custom rule set
        file("src/main/java/org/gradle/Class2.java") <<
            "package org.gradle; public class Class2 { public void doit() { boolean x = true; if (x) x = false; } }" // missing braces
    }

    private customRuleSet() {
        file("customRuleSet.xml") << customRuleSetText()
    }

    private static customRuleSetText() {
        String pathToRuleset = "category/java/codestyle.xml/ControlStatementBraces"
        if (versionNumber < VersionNumber.version(5)) {
            pathToRuleset = "rulesets/braces.xml"
        } else if (versionNumber < VersionNumber.version(6)) {
            pathToRuleset = "rulesets/java/braces.xml"
        } else if (versionNumber < VersionNumber.version(6, 13)) {
            pathToRuleset = "category/java/codestyle.xml/IfStmtsMustUseBraces"
        }
        """
            <ruleset name="custom"
                xmlns="http://pmd.sf.net/ruleset/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <description>Custom rule set</description>

                <rule ref="${pathToRuleset}"/>
            </ruleset>
        """
    }
}
