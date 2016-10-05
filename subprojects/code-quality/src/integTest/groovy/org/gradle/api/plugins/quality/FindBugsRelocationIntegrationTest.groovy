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

import static org.gradle.util.TextUtil.normaliseLineSeparators

class FindBugsRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":findbugs"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        // Has DM_EXIT
        file('src/main/java/org/gradle/BadClass.java') << 'package org.gradle; public class BadClass { public boolean isFoo(Object arg) { System.exit(1); return true; } }'

        buildFile << buildFileWithClassesDir("build/classes")
    }

    private static String buildFileWithClassesDir(String classesDir) {
        """
            apply plugin: "findbugs"

            repositories {
                mavenCentral()
            }

            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("$classesDir")
                dependencyCacheDir = file("build/dependency-cache")
                source "src/main/java"
                classpath = files()
            }

            task findbugs(type: FindBugs) {
                dependsOn compile
                classes = files("$classesDir")
                classpath = files()
                ignoreFailures = true
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        buildFile.text = buildFileWithClassesDir("build/other-classes")
        assert file("build/classes").directory
        file("build/classes").deleteDir()
    }

    @Override
    protected extractResults() {
        def contents = normaliseLineSeparators(file("build/reports/findbugs/findbugs.xml").text)
        contents = contents.replaceAll(/(\w+(?:imestamp|econds|mbytes)\w*)=".*?"/, '$1="[NUMBER]"')
        contents = contents.replaceAll(/<Jar>.*?<\/Jar>/, '<Jar>[JAR]</Jar>')
        contents = contents.split("\n")
            // Apparently FindBugs randomly reports ClassProfile entries
            .findAll { !it.contains("<ClassProfile") }
            .sort()
            .join("\n")
        return contents
    }
}
