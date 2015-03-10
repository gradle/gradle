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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildLayoutFactoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final BuildLayoutFactory locator = new BuildLayoutFactory()

    def "returns current directory when it contains a settings file"() {
        def currentDir = tmpDir.testDirectory
        def settingsFile = currentDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "looks for sibling directory called 'master' that it contains a settings file"() {
        def currentDir = tmpDir.createDir("current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "searches ancestors for a directory called 'master' that contains a settings file"() {
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "ignores 'master' directory when it does not contain a settings file"() {
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("sub/master")
        masterDir.createFile("gradle.properties")
        def settingsFile = tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == tmpDir.testDirectory
        layout.settingsDir == tmpDir.testDirectory
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "returns closest ancestor directory that contains a settings file"() {
        def currentDir = tmpDir.createDir("sub/current")
        def subDir = tmpDir.createDir("sub")
        def settingsFile = subDir.createFile("settings.gradle")
        tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == subDir
        layout.settingsDir == subDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "prefers the current directory as root directory"() {
        def currentDir = tmpDir.createDir("sub/current")
        def settingsFile = currentDir.createFile("settings.gradle")
        tmpDir.createFile("sub/settings.gradle")
        tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "prefers the 'master' directory over ancestor directory"() {
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("sub/master")
        def settingsFile = masterDir.createFile("settings.gradle")
        tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "returns start directory when search upwards is disabled"() {
        def currentDir = tmpDir.createDir("sub/current")
        tmpDir.createFile("sub/settings.gradle")
        tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, false)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)
    }

    def "returns current directory when no settings or wrapper properties files found"() {
        def currentDir = tmpDir.createDir("sub/current")

        expect:
        def layout = locator.getLayoutFor(currentDir, tmpDir.testDirectory)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)
    }

    def "can override build layout by specifying the settings file"() {
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile("settings.gradle")
        def rootDir = tmpDir.createDir("root")
        def settingsFile = rootDir.createFile("some-settings.gradle")
        def startParameter = new StartParameter()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = settingsFile
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == rootDir
        layout.settingsDir == rootDir
        refersTo(layout.settingsScriptSource, settingsFile)
    }

    def "can override build layout by specifying an empty settings script"() {
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile("settings.gradle")
        def startParameter = new StartParameter()
        startParameter.currentDir = currentDir
        startParameter.useEmptySettings()
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        isEmpty(layout.settingsScriptSource)
    }

    void refersTo(ScriptSource scriptSource, File file) {
        assert scriptSource instanceof UriScriptSource
        assert scriptSource.resource.sourceFile == file
    }

    void isEmpty(ScriptSource scriptSource) {
        assert scriptSource.resource.text == ''
    }
}
