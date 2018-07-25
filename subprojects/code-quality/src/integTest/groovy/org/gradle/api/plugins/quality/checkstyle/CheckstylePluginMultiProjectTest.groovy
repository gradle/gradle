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

import static org.gradle.util.TextUtil.getPlatformLineSeparator

class CheckstylePluginMultiProjectTest extends AbstractIntegrationSpec {

    private static final EXPECTED_DEPRECATION_MESSAGE = "Setting the Checkstyle configuration file under 'config/checkstyle' of a sub project has been deprecated. This is scheduled to be removed in Gradle 5.0. Use the root project's 'config/checkstyle' directory instead.";

    def "configures checkstyle extension to read config from root project in a single project build"() {
        given:
        buildFile << javaProjectUsingCheckstyle()
        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds('checkstyleMain')
        checkStyleReportFile(testDirectory).text.contains('Dummy.java')
    }

    def "configures checkstyle extension to read config just from sub project in a multi-project build and renders deprecation warning"() {
        given:
        executer.expectDeprecationWarning()
        settingsFile << "include 'child'"
        file('child/build.gradle') << javaProjectUsingCheckstyle()
        file('child/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:checkstyleMain')
        checkStyleReportFile(file('child')).text.contains('Dummy.java')
        outputContains(EXPECTED_DEPRECATION_MESSAGE)
    }

    def "configures checkstyle extension to read config from root project in a flat multi-project build"() {
        given:
        settingsFile << "include 'child:grand'"
        file('child/grand/build.gradle') << javaProjectUsingCheckstyle()
        file('child/grand/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:grand:checkstyleMain')
        checkStyleReportFile(file('child/grand')).text.contains('Dummy.java')
    }

    def "configures checkstyle extension to read config from root project in a deeply nested multi-project build"() {
        given:
        settingsFile << "include 'a:b:c'"
        file('a/b/c/build.gradle') << javaProjectUsingCheckstyle()
        file('a/b/c/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':a:b:c:checkstyleMain')
        checkStyleReportFile(file('a/b/c')).text.contains('Dummy.java')
    }

    def "configures checkstyle extension to read config from sub project in a multi-project build even if root project config is available and renders deprecation warning"() {
        given:
        executer.expectDeprecationWarning()
        settingsFile << "include 'child:grand'"
        file('child/grand/build.gradle') << javaProjectUsingCheckstyle()
        file('child/grand/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/grand/config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()
        file('config/checkstyle/checkstyle.xml') << invalidCheckStyleConfig()

        expect:
        succeeds(':child:grand:checkstyleMain')
        checkStyleReportFile(file('child/grand')).text.contains('Dummy.java')
        outputContains(EXPECTED_DEPRECATION_MESSAGE)
    }

    def "explicitly configures checkstyle extension to point to config directory and does not render deprecation message"() {
        given:
        settingsFile << "include 'child'"
        file('child/build.gradle') << javaProjectUsingCheckstyle()
        file('child/build.gradle') << """
            checkstyle {
                configDir = file('config/checkstyle')
            }
        """
        file('child/src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('child/config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(':child:checkstyleMain')
        checkStyleReportFile(file('child')).text.contains('Dummy.java')
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

    static File checkStyleReportFile(File projectDir) {
        new File(projectDir, 'build/reports/checkstyle/main.html')
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
}
