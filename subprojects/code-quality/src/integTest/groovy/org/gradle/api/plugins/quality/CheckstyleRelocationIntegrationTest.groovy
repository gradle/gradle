/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import java.util.regex.Pattern

class CheckstyleRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    private TestFile configFile

    @Override
    protected String getTaskName() {
        return ":checkstyle"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        configFile = file("config/checkstyle/checkstyle.xml") << """<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
          "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
          "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="RegexpSingleline">
       <property name="format" value="\\s+\$"/>
       <property name="minimum" value="0"/>
       <property name="maximum" value="0"/>
       <property name="message" value="Line has trailing spaces."/>
    </module>
</module>
        """

        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean is() { return true; } }  "
        file("src/main/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean is() { return true; } }"

        buildFile << buildFileWithSourceDir("src/main/java")
    }

    private static String buildFileWithSourceDir(String sourceDir) {
        """
            apply plugin: "checkstyle"

            repositories {
                mavenCentral()
            }

            task checkstyle(type: Checkstyle) {
                source "$sourceDir"
                classpath = files()
                ignoreFailures = true
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src").renameTo(file("other-src"))
        buildFile.text = buildFileWithSourceDir("other-src/main/java")
        def movedConfigPath = "config/checkstyle-config.xml"
        configFile.renameTo(file(movedConfigPath))
        buildFile << """
            checkstyle.configFile = file("$movedConfigPath")
        """
    }

    @Override
    protected extractResults() {
        return file("build/reports/checkstyle/checkstyle.xml").text.replaceAll(Pattern.quote(file().absolutePath + File.separator) + ".*?" + Pattern.quote(File.separator), "")
    }
}
