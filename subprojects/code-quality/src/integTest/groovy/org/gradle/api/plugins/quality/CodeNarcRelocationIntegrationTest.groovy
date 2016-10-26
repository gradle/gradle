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

import static org.gradle.util.TextUtil.normaliseLineSeparators

class CodeNarcRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    private TestFile configFile

    @Override
    protected String getTaskName() {
        return ":codenarc"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        configFile = file("config/codenarc/codenarc.xml") << """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
                     xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
                <ruleset-ref path='rulesets/naming.xml'/>
            </ruleset>
        """
        file("src/main/groovy/org/gradle/Class1.groovy") << """
            package org.gradle

            class Class1 {
                public static final int constant = 1
                public boolean is() { return true; }
            }
        """

        buildFile << buildFileWithSourceDir("src/main/groovy")
    }

    private static String buildFileWithSourceDir(String sourceDir) {
        """
            apply plugin: "codenarc"

            repositories {
                mavenCentral()
            }

            task codenarc(type: CodeNarc) {
                source "$sourceDir"
                ignoreFailures = true
                reports.html.enabled = false
                reports.text.enabled = true
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src").renameTo(file("other-src"))
        buildFile.text = buildFileWithSourceDir("other-src/main/groovy")
        def movedConfigPath = "config/codenarc-config.xml"
        configFile.renameTo(file(movedConfigPath))
        buildFile << """
            codenarc.configFile = file("$movedConfigPath")
        """
    }

    @Override
    protected extractResults() {
        return normaliseLineSeparators(file("build/reports/codenarc/codenarc.txt").text)
            .split("\n")
                .findAll { !it.startsWith("CodeNarc Report -") }
                .join("\n")
    }
}
