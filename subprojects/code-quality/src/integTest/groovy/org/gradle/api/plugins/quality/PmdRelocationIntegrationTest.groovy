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

class PmdRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":pmd"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean is() { return true; } }"
        file("src/main/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean is() { return true; } }"

        buildFile << buildFileWithSourceDir("src/main/java")
    }

    private static String buildFileWithSourceDir(String sourceDir) {
        """
            apply plugin: "java"
            apply plugin: "pmd"

            repositories {
                mavenCentral()
            }

            task pmd(type: Pmd) {
                source "$sourceDir"
                ignoreFailures = true
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src").renameTo(file("other-src"))
        buildFile.text = buildFileWithSourceDir("other-src/main/java")
    }

    @Override
    protected extractResults() {
        file("build/reports/pmd/pmd.xml").text
            .replaceAll(/timestamp=".*?"/, 'timestamp="[NUMBER]"')
    }
}
