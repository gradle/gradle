/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.fixtures

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.hamcrest.CoreMatchers.containsString

class WrapperTestFixture {

    public static final String GRADLEW_BASH_SCRIPT = "gradlew"
    public static final String GRADLEW_BATCH_SCRIPT = "gradlew.bat"
    public static final String GRADLEW_WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar"
    public static final String GRADLEW_PROPERTY_FILE = "gradle/wrapper/gradle-wrapper.properties"

    private final TestFile projectDirectory

    WrapperTestFixture(TestFile projectdirectory) {
        this.projectDirectory = projectdirectory
    }

    public void generated(String version = GradleVersion.current().version) {
        projectDirectory.file(GRADLEW_BASH_SCRIPT).assertExists()
        projectDirectory.file(GRADLEW_BATCH_SCRIPT).assertExists()
        projectDirectory.file(GRADLEW_WRAPPER_JAR).assertExists()
        projectDirectory.file(GRADLEW_PROPERTY_FILE).assertExists()
        projectDirectory.file(GRADLEW_PROPERTY_FILE).assertContents(containsString("gradle-${version}-bin.zip"))
    }

    public void notGenerated() {
        projectDirectory.file(GRADLEW_BASH_SCRIPT).assertDoesNotExist()
        projectDirectory.file(GRADLEW_BATCH_SCRIPT).assertDoesNotExist()
        projectDirectory.file(GRADLEW_WRAPPER_JAR).assertDoesNotExist()
        projectDirectory.file(GRADLEW_PROPERTY_FILE).assertDoesNotExist()
    }
}
