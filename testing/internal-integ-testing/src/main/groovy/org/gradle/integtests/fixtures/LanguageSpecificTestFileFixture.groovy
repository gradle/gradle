/*
 * Copyright 2024 the original author or authors.
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

import groovy.transform.SelfType
import org.gradle.test.fixtures.file.TestFile
import org.intellij.lang.annotations.Language

/**
 * Utility functions that write to files and provide syntax highlighting for the code snippets.
 *
 * @see Language
 */
@SelfType(AbstractIntegrationSpec)
trait LanguageSpecificTestFileFixture {

    /**
     * <b>Appends</b> provided code to the {@link #getBuildFile() default build file}.
     */
    TestFile buildFile(@GroovyBuildScriptLanguage String append) {
        buildFile << append
    }

    /**
     * <b>Appends</b> provided code to the given build file.
     */
    TestFile buildFile(String buildFile, @GroovyBuildScriptLanguage String append) {
        file(buildFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given build file.
     */
    TestFile buildFile(TestFile buildFile, @GroovyBuildScriptLanguage String append) {
        buildFile << append
    }

    /**
     * <b>Appends</b> provided code to the {@link #getSettingsFile() default settings file}.
     */
    TestFile settingsFile(@GroovySettingsScriptLanguage String append) {
        settingsFile << append
    }

    /**
     * <b>Appends</b> provided code to the given settings file.
     */
    TestFile settingsFile(String settingsFile, @GroovySettingsScriptLanguage String append) {
        file(settingsFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given settings file.
     */
    TestFile settingsFile(TestFile settingsFile, @GroovySettingsScriptLanguage String append) {
        settingsFile << append
    }

    /**
     * <b>Appends</b> provided code to the {@link #getInitScriptFile() default init script file}.
     */
    TestFile initScriptFile(@GroovyInitScriptLanguage String append) {
        initScriptFile << append
    }

    /**
     * <b>Appends</b> provided code to the given init script file.
     */
    TestFile initScriptFile(String initScriptFile, @GroovyInitScriptLanguage String append) {
        file(initScriptFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given init script file.
     */
    TestFile initScriptFile(TestFile initScriptFile, @GroovyInitScriptLanguage String append) {
        initScriptFile << append
    }

    /**
     * <b>Appends</b> provided code to the given Java file.
     */
    TestFile javaFile(String targetFile, @Language('java') String append) {
        file(targetFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given Java file.
     */
    TestFile javaFile(TestFile targetBuildFile, @Language('java') String append) {
        targetBuildFile << append
    }

    /**
     * <b>Appends</b> provided code to the given Groovy file.
     * <p>
     * Consider specialized methods for Gradle scripts:
     * <ul>
     * <li>{@link #buildFile(java.lang.String, java.lang.String)}
     * <li>{@link #settingsFile(java.lang.String, java.lang.String)}
     * <li>{@link #initScriptFile(java.lang.String, java.lang.String)}
     * </ul>
     */
    TestFile groovyFile(String targetFile, @Language('groovy') String append) {
        file(targetFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given Groovy file.
     * <p>
     * Consider specialized methods for Gradle scripts:
     * <ul>
     * <li>{@link #buildFile(org.gradle.test.fixtures.file.TestFile, java.lang.String)}
     * <li>{@link #settingsFile(org.gradle.test.fixtures.file.TestFile, java.lang.String)}
     * <li>{@link #initScriptFile(org.gradle.test.fixtures.file.TestFile, java.lang.String)}
     * </ul>
     */
    TestFile groovyFile(TestFile targetFile, @Language('groovy') String append) {
        targetFile << append
    }

    /**
     * <b>Appends</b> provided code to the given Kotlin file.
     * <p>
     * Consider specialized methods for Kotlin scripts.
     */
    TestFile kotlinFile(String targetFile, @Language('kotlin') String append) {
        file(targetFile) << append
    }

    /**
     * <b>Appends</b> provided code to the given Kotlin file.
     * <p>
     * Consider specialized methods for Kotlin scripts.
     */
    TestFile kotlinFile(TestFile targetFile, @Language('kotlin') String append) {
        targetFile << append
    }

    /**
     * <b>Appends</b> provided code to the {@link #getVersionCatalogFile() default version catalog file}.
     */
    TestFile versionCatalogFile(@Language("toml") String append) {
        versionCatalogFile << append
    }

    /**
     * Provides syntax highlighting for the snippet of the build script code.
     *
     * @return the same snippet
     */
    String buildScriptSnippet(@GroovyBuildScriptLanguage String snippet) {
        snippet
    }

    /**
     * Provides syntax highlighting for the snippet of the settings script code.
     *
     * @return the same snippet
     */
    String settingsScriptSnippet(@GroovySettingsScriptLanguage String snippet) {
        snippet
    }

    /**
     * Provides syntax highlighting for the snippet of the init script code.
     *
     * @return the same snippet
     */
    String initScriptSnippet(@GroovyInitScriptLanguage String snippet) {
        snippet
    }

    /**
     * Provides syntax highlighting for the snippet of Java code.
     *
     * @return the same snippet
     */
    String javaSnippet(@Language('java') String snippet) {
        snippet
    }

    /**
     * Provides syntax highlighting for the snippet of Groovy code.
     * <p>
     * Consider specialized methods for Gradle scripts:
     * <ul>
     * <li>{@link #buildScriptSnippet(java.lang.String)}
     * <li>{@link #settingsScriptSnippet(java.lang.String)}
     * <li>{@link #initScriptSnippet(java.lang.String)}
     * </ul>
     *
     * @return the same snippet
     */
    String groovySnippet(@Language('groovy') String snippet) {
        snippet
    }
}
