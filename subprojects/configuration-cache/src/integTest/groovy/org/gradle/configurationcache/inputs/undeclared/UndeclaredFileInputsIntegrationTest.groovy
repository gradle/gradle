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

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented

import static org.gradle.configurationcache.inputs.undeclared.FileUtils.testFilePath
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.Matchers.allOf

class UndeclaredFileInputsIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements GroovyPluginImplementation {
    @ToBeImplemented("reporting multiple consumers per input is not implemented yet")
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
            // TODO when implemented, this assertion should be replaced with the commented one:
            inputs.expect(allOf(startsWith("Plugin class 'SneakyPluginA': file '${FileUtils.testFileName}'"), containsString(FileUtils.testFileName)))
            /*
            pluginClasses.forEach {
                inputs = inputs.expect(allOf(startsWith("Plugin class '$it':"), containsString(FileUtils.testFileName))))
            }*/
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
            groovyPluginReadingValue(file("buildSrc/src/main/groovy/${it}.groovy"), inputRead, it)
        }
        buildFile << pluginClasses.collect { "apply plugin: $it" }.join("\n")
    }

    private static void groovyPluginReadingValue(TestFile sourceFile, BuildInputRead read, String pluginClassName) {
        sourceFile << """
            import ${Project.name}
            import ${Plugin.name}

            ${read.requiredImports().collect { "import $it" }.join("\n")}

            @${CompileStatic.name}
            class $pluginClassName implements Plugin<Project> {
                public void apply(Project project) {
                    def value = ${read.groovyExpression}
                    println("apply = " + value)
                }
            }
        """
    }
}
