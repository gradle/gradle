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
import static org.hamcrest.Matchers.not

class PmdPluginIntegrationTest extends WellBehavedPluginTest {
    def setup() {
        writeBuildFile()
    }

    @Override
    String getMainTask() {
        return "check"
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


    /**
     * bad test class can't be parsed due to usage of Generics
     * @TODO: Allow to expose this error from ant pmd.
     * */
    def "can configure targetJdk"() {
        badCode()
        buildFile << """
                pmd {
                    targetJdk = 1.4
                }
            """
        expect:
        succeeds("check")
    }

    def "can configure reporting"() {
        given:
        goodCode()

        and:
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

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "java"
apply plugin: "pmd"

repositories {
    mavenCentral()
}
        """
    }

    private Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private goodCode() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; class Class1Test { public boolean isFoo(Object arg) { return true; } }"
    }

    private badCode() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; class Class1Test<T> { {} public boolean equals(Object arg) { return true; } }"

    }
}
