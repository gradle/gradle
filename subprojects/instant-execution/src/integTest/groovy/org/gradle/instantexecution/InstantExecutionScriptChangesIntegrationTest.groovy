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

package org.gradle.instantexecution

import groovy.transform.Canonical
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test
import spock.lang.Unroll

class InstantExecutionScriptChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    @Test
    def "invalidates cache upon changes to #testLabel"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = scriptChangeFixtureFor(language, scriptType, scriptDiscovery)
        def scriptFile = fixture.scriptFile
        def build = { instantRun('help', *fixture.buildArguments) }

        when:
        scriptFile.text = 'println("Hello!")'
        build()

        then:
        outputContains 'Hello!'

        when:
        scriptFile.text = 'println("Hi!")'
        build()

        then:
        outputContains 'Hi!'
        instant.assertStateStored()

        when:
        build()

        then:
        outputDoesNotContain 'Hi'
        instant.assertStateLoaded()

        where:
        [scriptDiscovery_, language_, scriptType_] << [
            ScriptDiscovery.values(),
            ScriptLanguage.values(),
            ScriptType.values()
        ].combinations()
        language = language_ as ScriptLanguage
        scriptType = scriptType_ as ScriptType
        scriptDiscovery = scriptDiscovery_ as ScriptDiscovery
        testLabel = "$scriptDiscovery $language $scriptType script".toLowerCase()
    }

    @Canonical
    static class ScriptChangeFixture {
        TestFile scriptFile
        List<String> buildArguments
    }

    private ScriptChangeFixture scriptChangeFixtureFor(
        ScriptLanguage language, ScriptType scriptType, ScriptDiscovery scriptDiscovery
    ) {
        def scriptFileExtension = language == ScriptLanguage.GROOVY ? ".gradle" : ".gradle.kts"
        def defaultScriptFile = file("${baseScriptFileNameFor(scriptType)}$scriptFileExtension")
        def buildArguments = scriptType == ScriptType.INIT
            ? ["-I", defaultScriptFile.absolutePath]
            : []
        switch (scriptDiscovery) {
            case ScriptDiscovery.DEFAULT:
                return new ScriptChangeFixture(defaultScriptFile, buildArguments)
            case ScriptDiscovery.APPLIED:
                String appliedScriptName = "applied${scriptFileExtension}"
                TestFile appliedScriptFile = new TestFile(defaultScriptFile.parentFile, appliedScriptName)
                defaultScriptFile.text = language == ScriptLanguage.GROOVY
                    ? "apply from: './$appliedScriptName'"
                    : "apply(from = \"./$appliedScriptName\")"
                return new ScriptChangeFixture(appliedScriptFile, buildArguments)
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
}
