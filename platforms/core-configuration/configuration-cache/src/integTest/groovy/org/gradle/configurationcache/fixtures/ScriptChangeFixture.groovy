/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import groovy.transform.Immutable
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.internal.GFileUtils.relativePathOf

class ScriptChangeFixture {

    static List<Spec> specs() {
        [
            ScriptDiscovery.values(),
            ScriptLanguage.values(),
            ScriptType.values()
        ].combinations().collect { ScriptDiscovery d, ScriptLanguage l, ScriptType t ->
            new Spec(d, l, t)
        }
    }

    @Immutable
    static class Spec {
        ScriptDiscovery scriptDiscovery
        ScriptLanguage scriptLanguage
        ScriptType scriptType

        @Override
        String toString() {
            "$scriptDiscovery $scriptLanguage $scriptType script".toLowerCase()
        }

        ScriptChangeFixture fixtureForProjectDir(TestFile projectDir, TestFile rootProjectDir = projectDir) {
            def scriptFileExtension = scriptLanguage == ScriptLanguage.GROOVY ? '.gradle' : '.gradle.kts'
            def defaultScriptFile = projectDir.file("${baseScriptFileNameFor(scriptType)}$scriptFileExtension")
            def buildArguments = scriptType == ScriptType.INIT
                ? ['help', '-I', defaultScriptFile.absolutePath]
                : ['help']
            switch (scriptDiscovery) {
                case ScriptDiscovery.DEFAULT:
                    return new ScriptChangeFixture(rootProjectDir, defaultScriptFile, buildArguments)
                case ScriptDiscovery.APPLIED:
                    String appliedScriptName = "applied${scriptFileExtension}"
                    TestFile appliedScriptFile = new TestFile(defaultScriptFile.parentFile, appliedScriptName)
                    defaultScriptFile.text = scriptLanguage == ScriptLanguage.GROOVY
                        ? "apply from: './$appliedScriptName'"
                        : "apply(from = \"./$appliedScriptName\")"
                    return new ScriptChangeFixture(rootProjectDir, appliedScriptFile, buildArguments)
            }
        }

        private static String baseScriptFileNameFor(ScriptType type) {
            switch (type) {
                case ScriptType.PROJECT:
                    return "build"
                case ScriptType.SETTINGS:
                    return "settings"
                case ScriptType.BUILDSRC_PROJECT:
                    return "buildSrc/build"
                case ScriptType.BUILDSRC_SETTINGS:
                    return "buildSrc/settings"
                case ScriptType.INIT:
                    return "gradle/my.init"
            }
        }
    }

    enum ScriptDiscovery {
        DEFAULT,
        APPLIED
    }

    enum ScriptLanguage {
        GROOVY,
        KOTLIN
    }

    enum ScriptType {
        PROJECT,
        SETTINGS,
        INIT,
        BUILDSRC_PROJECT,
        BUILDSRC_SETTINGS,
    }

    final TestFile projectDir
    final TestFile scriptFile
    final List<String> buildArguments
    final String expectedOutputBeforeChange = 'Hello!'
    final String expectedOutputAfterChange = 'Hi!'

    ScriptChangeFixture(TestFile projectDir, TestFile scriptFile, List<String> buildArguments) {
        this.projectDir = projectDir
        this.scriptFile = scriptFile
        this.buildArguments = buildArguments
    }

    String getExpectedCacheInvalidationMessage() {
        def scriptPath = relativePathOf(scriptFile, projectDir)
        def scriptDesc = scriptPath.contains('init') ? 'init script' : 'file'
        return "configuration cache cannot be reused because $scriptDesc '$scriptPath' has changed."
    }

    void setup() {
        scriptFile.text = "println(\"$expectedOutputBeforeChange\")"
    }

    void applyChange() {
        scriptFile.text = "println(\"$expectedOutputAfterChange\")"
    }
}
