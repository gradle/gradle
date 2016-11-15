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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue("gradle/gradle#855")
class CheckstylePluginClasspathIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        writeBuildFile()
        writeConfigFile()
        goodCode()
    }

    def "accepts throwing exception from other project"() {
        succeeds("checkstyleMain")
    }

    private void writeBuildFile() {
        file("settings.gradle") << """
include "api"
include "client"
        """

        file("build.gradle") << """
subprojects {
    apply plugin: "java"
    apply plugin: "checkstyle"

    repositories {
        mavenCentral()
    }

    checkstyle.toolVersion = "0.7.2"
}

project("client") {
    dependencies {
        compile project("api")
    }
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
        <module name="JavadocMethod"/>
    </module>
</module>
        """
    }

    private void goodCode() {
        file("api/src/main/java/csbug/Exn.java") << """
package csbug;

class Exn extends Exception { }
        """

        file("client/src/main/java/csbug/Iface.java") << """
package csbug;

interface Iface {
    /**
     * @throws Exn
     * @throws IllegalArgumentException
     */
    void foo() throws Exn;
}
        """
    }
}
