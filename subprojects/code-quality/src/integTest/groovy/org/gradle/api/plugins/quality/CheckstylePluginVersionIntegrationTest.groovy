/*
 * Copyright 2015 the original author or authors.
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

@TargetVersions(['6.8.2', '6.8', '6.5', '6.0', '5.9', '5.5'])
class CheckstylePluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    def "can use checkstyle version"() {
        given:
        badCode()
        goodCode()

        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>"""

        buildFile << """
            apply plugin: "java"
            apply plugin: "checkstyle"
            repositories {
                mavenCentral()
            }
            checkstyle {
                toolVersion = '$version'
            }
        """

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(containsString("Checkstyle rule violations were found. See the report at:"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(not(containsClass("org.gradle.Class2")))
    }

    private void goodCode() {
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
        file('src/test/groovy/org/gradle/TestClass2.java') << 'package org.gradle; class Class2 { }'
    }

    private badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/test/java/org/gradle/testclass1.java") << "package org.gradle; class testclass1 { }"
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }
}
