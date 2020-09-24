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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import spock.lang.Issue

@Issue("gradle/gradle#855")
@TargetCoverage({ CheckstyleCoverage.getSupportedVersionsByJdk() })
class CheckstylePluginClasspathIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        writeBuildFiles()
        writeConfigFile()
        goodCode()
    }

    def "accepts throwing exception from other project"() {
        expect:
        succeeds("checkstyleMain")
    }

    private void writeBuildFiles() {
        file("settings.gradle") << """
include "api"
include "client"
        """

        file("build.gradle") << """
subprojects {
    apply plugin: "java"
    apply plugin: "checkstyle"

    ${mavenCentralRepository()}

    checkstyle {
        toolVersion = '$version'
        configFile rootProject.file("checkstyle.xml")
    }
}

project("client") {
    dependencies {
        implementation project(":api")
    }
}
        """
    }

    private void writeConfigFile() {
        file("checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="JavadocMethod"/>
    </module>
</module>
        """
    }

    private void goodCode() {
        file("api/src/main/java/org/gradle/FooException.java") << """
package org.gradle;

class FooException extends Exception { }
        """

        file("client/src/main/java/org/gradle/Iface.java") << """
package org.gradle;

interface Iface {
    /**
     * Method Description.
     *
     * @throws FooException whenever
     * @throws IllegalArgumentException otherwise
     */
    void foo() throws FooException, IllegalArgumentException;
}
        """
    }
}
