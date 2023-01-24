/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared

import groovy.test.NotYetImplemented
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.configurationcache.inputs.undeclared.FileUtils.testFilePath
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.Matchers.allOf

class UndeclaredFileInputsIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements GroovyPluginImplementation {
    @NotYetImplemented
    def "reports multiple consumers of a single file in #accessKind access"() {
        def configurationCache = newConfigurationCacheFixture()

        UndeclaredFileAccess access = inputRead(testDirectory)
        applyBuildLogic(access)
        def accessedFile = new File(access.filePath)

        when:
        accessedFile.text = "foo"
        configurationCacheRunLenient()

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            pluginClasses.forEach {
                inputs = inputs
                    .expect(allOf(startsWith("Plugin class '$it':"), containsString(FileUtils.testFileName)))
                    // These are accessed by the plugin template used in this test:
                    .expect(startsWith("Plugin class '$it': system property 'INSTANCE'"))
                    .expect(startsWith("Plugin class '$it': system property 'CLOSURE'"))
            }
        }

        where:
        accessKind          | inputRead
        "file"              | (TestFile it) -> { UndeclaredFileAccess.fileText(testFilePath(it)) }
        "file system entry" | (TestFile it) -> { UndeclaredFileAccess.fileExists(testFilePath(it)) }
        "directory content" | (TestFile it) -> { UndeclaredFileAccess.directoryContent(testFilePath(it)) }
    }

    private List<String> pluginClasses = ["SneakyPluginA", "SneakyPluginB"]

    private void applyBuildLogic(BuildInputRead inputRead) {
        pluginClasses.forEach {
            staticGroovyPlugin(file("buildSrc/src/main/groovy/${it}.groovy"), inputRead, it)
        }
        buildFile << pluginClasses.collect { "apply plugin: $it" }.join("\n")
    }
}
