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
package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.internal.TextUtil.getPlatformLineSeparator
import static org.hamcrest.CoreMatchers.containsString

class CheckstylePluginMultiProjectTest extends AbstractIntegrationSpec {

    def "configures checkstyle extension to read config from root project in a single project build"() {
        given:
        buildFile << javaProjectUsingCheckstyle()
        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds('checkstyleMain')
        checkStyleReportFile(testDirectory).assertExists()
    }

    def "fails when root project does contain config in default location"() {
        given:
        settingsFile << "include 'child'"
        file('child/build.gradle') << javaProjectUsingCheckstyle()
        file('child/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        fails(':child:checkstyleMain')
        checkStyleReportFile(file('child')).assertDoesNotExist()
    }

    def "configures checkstyle extension to read config from root project in a flat multi-project build"() {
        given:
        settingsFile << "include 'child:grand'"
        file('child/grand/build.gradle') << javaProjectUsingCheckstyle()
        file('child/grand/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:grand:checkstyleMain')
        checkStyleReportFile(file('child/grand')).assertExists()
    }

    def "configures checkstyle extension to read config from root project in a deeply nested multi-project build"() {
        given:
        settingsFile << "include 'a:b:c'"
        file('a/b/c/build.gradle') << javaProjectUsingCheckstyle()
        file('a/b/c/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':a:b:c:checkstyleMain')
        checkStyleReportFile(file('a/b/c')).assertExists()
    }

    def "configures checkstyle extension to read config from root project in a multi-project build even if sub project config is available"() {
        given:
        settingsFile << "include 'child:grand'"
        file('child/grand/build.gradle') << javaProjectUsingCheckstyle()
        file('child/grand/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/grand/config/checkstyle/checkstyle.xml') << invalidCheckStyleConfig()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:grand:checkstyleMain')
        checkStyleReportFile(file('child/grand')).assertExists()
    }

    def "explicitly configures checkstyle extension to point to config directory"() {
        given:
        settingsFile << "include 'child'"
        file('child/build.gradle') << javaProjectUsingCheckstyle()
        file('child/build.gradle') << """
            checkstyle {
                configDirectory = file('config/checkstyle')
            }
        """
        file('child/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:checkstyleMain')
        checkStyleReportFile(file('child')).assertExists()
    }

    static String simpleCheckStyleConfig() {
        """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="NewlineAtEndOfFile"/>
</module>
        """
    }

    static String invalidCheckStyleConfig() {
        'INVALID AND SHOULD NEVER BE READ'
    }

    static TestFile checkStyleReportFile(File projectDir) {
        new TestFile(projectDir, 'build/reports/checkstyle/main.html')
    }

    static String javaProjectUsingCheckstyle() {
        """
            apply plugin: 'java'
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}
        """
    }

    static String javaClassWithNewLineAtEnd() {
        "public class Dummy {}${getPlatformLineSeparator()}"
    }

    static String multilineJavaClass() {
        return """
            package org.sample;

            class MyClass {
              int i = 0;
            }
            """
    }

    static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }
}
