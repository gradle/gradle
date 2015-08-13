/*
 * Copyright 2013 the original author or authors.
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

import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class PmdPluginVersionIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "pmd"

            repositories {
                mavenCentral()
            }

            pmd {
                toolVersion = '$version'
            }
        """
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

    void "can configure priority level threshold"() {
        badCode()
        buildFile << """
            pmd {
                rulePriority = 2
            }
        """

        expect:
        fails("check")
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").
            assertContents(containsClass("org.gradle.Class1Test")).
            assertContents(containsLine(containsString('BooleanInstantiation'))).
            assertContents(not(containsLine(containsString('OverrideBothEqualsAndHashcode'))))
    }

    def "gets reasonable message when priority level threshold is out of range from extension"() {
        goodCode()
        buildFile << """
        pmd {
            rulePriority = 11
        }
"""
        expect:
        fails("check")
        errorOutput.contains("Invalid rulePriority '11'.  Valid range 1 (highest) to 5 (lowest).")
    }

    def "gets reasonable message when priority level threshold is out of range from task"() {
        goodCode()
        buildFile << """
        pmdMain {
            rulePriority = 11
        }
"""
        expect:
        fails("check")
        errorOutput.contains("Invalid rulePriority '11'.  Valid range 1 (highest) to 5 (lowest).")
    }

    def "can configure reporting"() {
        goodCode()
        buildFile << """
            pmdMain {
                reports {
                    xml.enabled false
                    html.destination "htmlReport.html"
                }
            }
        """

        expect:
        succeeds("check")
        !file("build/reports/pmd/main.xml").exists()
        file("htmlReport.html").exists()
    }

    def "use custom rule set files"() {
        customCode()
        customRuleSet()

        buildFile << """
            pmd {
                ruleSets = []
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

    def "use custom rule set"() {
        customCode()

        buildFile << """
            pmd {
                ruleSets = []
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
        output.contains "Class1Test.java:1:\tAvoid instantiating Boolean objects"
        output.contains "Class1Test.java:1:\tEnsure you override both equals() and hashCode()"
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
        // PMD Lvl 2 Warning BooleanInstantiation
        // PMD Lvl 3 Warning OverrideBothEqualsAndHashcode
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test<T> { public boolean equals(Object arg) { return java.lang.Boolean.valueOf(true); } }"
    }

    private customCode() {
        // class that would fail basic rule set but doesn't fail custom rule set
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; public class Class1 { public void doit() { boolean x = true; if (x) {} } }" // empty then-block
        // class that wouldn't fail basic rule set but does fail custom rule set
        file("src/main/java/org/gradle/Class2.java") <<
            "package org.gradle; public class Class2 { public void doit() { boolean x = true; if (x) x = false; } }" // missing braces
    }

    private customRuleSet() {
        file("customRuleSet.xml") << customRuleSetText()
    }

    private customRuleSetText() {
        """
            <ruleset name="custom"
                xmlns="http://pmd.sf.net/ruleset/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <description>Custom rule set</description>

                <rule ref="rulesets/java/braces.xml"/>
            </ruleset>
        """
    }
}
