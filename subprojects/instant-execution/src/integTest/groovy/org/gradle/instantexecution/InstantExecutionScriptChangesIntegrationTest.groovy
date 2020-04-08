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

import org.gradle.test.fixtures.file.TestFile
import org.junit.Test
import spock.lang.Unroll

class InstantExecutionScriptChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    @Test
    def "invalidates cache upon changes to #language #scriptType script"() {
        given:
        def instant = newInstantExecutionFixture()
        def scriptFile = scriptFileFor(language, scriptType).tap { parentFile.mkdirs() }
        List<String> buildArgs = buildArgumentsFor(scriptType, scriptFile)
        def build = { instantRun('help', *buildArgs) }

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
        [language_, scriptType_] << [ScriptLanguage.values(), ScriptType.values()].combinations()
        language = language_ as ScriptLanguage
        scriptType = scriptType_ as ScriptType
    }

    private TestFile scriptFileFor(ScriptLanguage language, ScriptType type) {
        switch (type) {
            case ScriptType.PROJECT:
                return file("build${language.fileExtension}")
            case ScriptType.SETTINGS:
                return file("settings${language.fileExtension}")
            case ScriptType.BUILDSRC_PROJECT:
                return file("buildSrc/build${language.fileExtension}")
            case ScriptType.BUILDSRC_SETTINGS:
                return file("buildSrc/settings${language.fileExtension}")
            case ScriptType.INIT:
                return file("gradle/my.init${language.fileExtension}")
        }
    }

    private List<String> buildArgumentsFor(ScriptType type, TestFile scriptFile) {
        switch (type) {
            case ScriptType.INIT:
                return ["-I", scriptFile.absolutePath]
            default:
                return []
        }
    }

    enum ScriptType {
        PROJECT,
        SETTINGS,
        INIT,
        BUILDSRC_PROJECT,
        BUILDSRC_SETTINGS,
    }

    enum ScriptLanguage {

        GROOVY{
            @Override
            String getFileExtension() { ".gradle" }
        },

        KOTLIN{
            @Override
            String getFileExtension() { ".gradle.kts" }
        };

        abstract String getFileExtension();
    }
}
