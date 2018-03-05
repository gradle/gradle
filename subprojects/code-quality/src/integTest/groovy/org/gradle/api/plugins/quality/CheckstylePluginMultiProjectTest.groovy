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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CheckstylePluginMultiProjectTest extends AbstractIntegrationSpec {

    def "configures checkstyle extension to read config from root project"() {
        given:
        def rootProject = multiProjectBuild('rootCheckStyle', ['child']) {
            settingsFile << """
include 'child:grand'
"""
        }
        rootProject.file("child/grand/build.gradle") << """
apply plugin: "java"
apply plugin: "checkstyle"

${mavenCentralRepository()}
"""
        rootProject.file('child/grand/src/main/java/Dummy.java') << "public class Dummy {}\n"
        rootProject.file('child/grand', 'config/checkstyle/checkstyle.xml') << "INVALID AND SHOULD NEVER BE READ"
        rootProject.file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        expect:
        succeeds(":child:grand:checkstyleMain")
        checkStyleReportFile(rootProject.file('child/grand')).text.contains('Dummy.java')
    }

    private static String simpleCheckStyleConfig() {
        """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="NewlineAtEndOfFile"/>
</module>
        """
    }

    private static File checkStyleReportFile(File projectDir) {
        new File(projectDir, 'build/reports/checkstyle/main.html')
    }
}
