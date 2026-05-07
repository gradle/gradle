/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.file.TestFile

@CompileStatic
trait CommonTestFilesFixture {

    /**
     * The root test directory, in which common test files are located,
     * such as the settings script, root project build script.
     */
    abstract TestFile getUserActionRootDir()

    /**
     * Default Groovy build script file - {@code build.gradle}
     * <p>
     * For syntax highlighting in IDEA use {@link LanguageSpecificTestFileFixture#buildFile(String)} or its overloads.
     */
    TestFile getBuildFile() {
        userActionRootDir.file("build.gradle")
    }

    /**
     * Default Groovy settings script file - {@code settings.gradle}
     * <p>
     * For syntax highlighting in IDEA use {@link LanguageSpecificTestFileFixture#settingsFile(String)} or its overloads.
     */
    TestFile getSettingsFile() {
        userActionRootDir.file("settings.gradle")
    }

    /**
     * An init script file - {@code init.gradle}
     * <p>
     * For syntax highlighting in IDEA use {@link LanguageSpecificTestFileFixture#initScriptFile(String)} or its overloads.
     */
    TestFile getInitScriptFile() {
        userActionRootDir.file("init.gradle")
    }

    /**
     * Root build properties file - {@code gradle.properties}
     * <p>
     * For syntax highlighting in IDEA use {@link LanguageSpecificTestFileFixture#propertiesFile(String)} or its overloads.
     */
    TestFile getPropertiesFile() {
        userActionRootDir.file("gradle.properties")
    }

    /**
     * Default version catalog file - {@code gradle/libs.versions.toml}
     * <p>
     * For syntax highlighting in IDEA use {@link LanguageSpecificTestFileFixture#versionCatalogFile(String)} or its overloads.
     */
    TestFile getVersionCatalogFile() {
        userActionRootDir.file("gradle/libs.versions.toml")
    }
}
