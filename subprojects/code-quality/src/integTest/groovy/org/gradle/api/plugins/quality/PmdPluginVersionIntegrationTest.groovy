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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

@TargetVersions(['4.3', '5.0.2'])
class PmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {
    def "can use different PMD versions"() {
        given:
        badCode()
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

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'")
        failure.assertThatCause(containsString("2 PMD rule violations were found. See the report at:"))
        file("build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.Class1")))
        file("build/reports/pmd/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    private badCode() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; class Class1Test { {} public boolean equals(Object arg) { return true; } }"
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }
}
