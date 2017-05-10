/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization.layout

import org.gradle.StartParameter
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.internal.scripts.DefaultScriptFileResolver
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.scripts.ScriptingLanguage
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class BuildLayoutFactoryTest extends Specification {

    // This pair of constants is used to unroll most of the tests in this class
    static final def SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS =
        [[], ['.gradle.kts'], ['.gradle.kts'], ['.gradle.kts', '.tic'], ['.gradle.kts', '.tac'], ['.tic', '.gradle.kts'], ['.tac', '.gradle.kts']]
    static final def SETTINGS_FILENAME_PERMUTATIONS =
        ['settings.gradle', 'settings.gradle', 'settings.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'settings.gradle', 'settings.gradle.kts']

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Unroll
    def "returns current directory when it contains a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.testDirectory
        def settingsFile = currentDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "returns current directory when no ancestor directory contains a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and: "temporary tree created out of the Gradle build tree"
        def tmpDir = File.createTempFile("stop-", "-at").canonicalFile
        def stopAt = new File(tmpDir, 'stopAt')
        def currentDir = new File(new File(stopAt, "intermediate"), 'current')
        currentDir.mkdirs()

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)

        cleanup: "temporary tree"
        tmpDir.deleteDir()

        where:
        extensions << [[], ['.gradle.kts']]
    }

    @Unroll
    def "looks for sibling directory called 'master' that it contains a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "searches ancestors for a directory called 'master' that contains a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "ignores 'master' directory when it does not contain a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("sub/master")
        masterDir.createFile("gradle.properties")
        def settingsFile = tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == tmpDir.testDirectory
        layout.settingsDir == tmpDir.testDirectory
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "returns closest ancestor directory that contains a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def subDir = tmpDir.createDir("sub")
        def settingsFile = subDir.createFile(settingsFilename)
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == subDir
        layout.settingsDir == subDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "prefers the current directory as root directory with a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def settingsFile = currentDir.createFile(settingsFilename)
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "prefers the 'master' directory over ancestor directory with a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("sub/master")
        def settingsFile = masterDir.createFile(settingsFilename)
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "returns start directory when search upwards is disabled with a #settingsFilename file when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, false)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    @Unroll
    def "returns current directory when no settings or wrapper properties files found when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("sub/current")

        expect:
        def layout = locator.getLayoutFor(currentDir, tmpDir.testDirectory)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)

        where:
        extensions << [[], ['.gradle.kts']]
    }

    @Unroll
    def "can override build layout by specifying the settings file to #overrideSettingsFilename with existing #settingsFilename when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def rootDir = tmpDir.createDir("root")
        def settingsFile = rootDir.createFile(overrideSettingsFilename)
        def startParameter = new StartParameter()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = settingsFile
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == rootDir
        layout.settingsDir == rootDir
        refersTo(layout.settingsScriptSource, settingsFile)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
        overrideSettingsFilename << SETTINGS_FILENAME_PERMUTATIONS.collect { "some-$it" }
    }

    @Unroll
    def "can override build layout by specifying an empty settings script with existing #settingsFilename when script languages #extensions"() {
        given:
        def locator = new BuildLayoutFactory(scriptFileResolver(extensions))

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def startParameter = new StartParameter()
        startParameter.currentDir = currentDir
        startParameter.useEmptySettings()
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)

        where:
        extensions << SCRIPT_LANGUAGE_EXTENSION_PERMUTATIONS
        settingsFilename << SETTINGS_FILENAME_PERMUTATIONS
    }

    ScriptFileResolver scriptFileResolver(List<String> extensions) {
        DefaultScriptFileResolver.forScriptingLanguages(extensions.collect { extension ->
            Stub(ScriptingLanguage) { getExtension() >> extension }
        })
    }

    void refersTo(ScriptSource scriptSource, File file) {
        assert scriptSource instanceof UriScriptSource
        assert scriptSource.resource.sourceFile == file
    }

    void isEmpty(ScriptSource scriptSource) {
        assert scriptSource.resource.text == ''
    }
}
