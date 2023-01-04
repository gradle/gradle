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
package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CheckstylePluginDependenciesIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        writeBuildFile()
        writeConfigFile()
        badCode()
    }

    def "allows configuring tool dependencies explicitly"() {
        //Language has to be English, because the error message is localised
        defaultLocale('en')

        expect:
        succeeds("dependencies", "--configuration", "checkstyle")
        output.contains "com.puppycrawl.tools:checkstyle:"

        when:
        buildFile << """
            dependencies {
                //downgrade version:
                checkstyle "com.puppycrawl.tools:checkstyle:5.5"
            }
        """

        then:
        fails("check")
        failure.assertHasErrorOutput("Name 'class1' must match pattern")

        and:
        succeeds("dependencies", "--configuration", "checkstyle")
        output.contains "com.puppycrawl.tools:checkstyle:5.5"
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "groovy"
apply plugin: "checkstyle"

${mavenCentralRepository()}

dependencies {
    implementation localGroovy()
}
        """
    }

    private void writeConfigFile() {
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """
    }

    private void badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
    }

    private void defaultLocale(String defaultLocale) {
        executer.withDefaultLocale(new Locale(defaultLocale))
    }
}
