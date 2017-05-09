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

class JDependRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":jdepend"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean is() { return true; } }"
        file("src/main/java/org/gradle/Class1Test.java") <<
            "package org.gradle; class Class1Test { public boolean is() { return true; } }"

        buildFile << buildFileWithClassesDir("build/classes")
    }

    private static String buildFileWithClassesDir(String classesDir) {
        """
            apply plugin: "jdepend"

            repositories {
                mavenCentral()
            }

            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("$classesDir")
                source "src/main/java"
                classpath = files()
            }

            task jdepend(type: JDepend) {
                dependsOn compile
                classesDirs = files("$classesDir")
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
        file("build/reports/jdepend/jdepend.xml").text
    }
}
