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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.scripts.ScriptFileUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildLayoutFactoryTest extends Specification {

    static final def TEST_CASES = ScriptFileUtil.getValidSettingsFileNames()

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "returns current directory when it contains a #settingsFilename file"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.testDirectory
        def settingsFile = currentDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == settingsFile
        !layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns current directory when no ancestor directory contains a settings file or build file"() {
        given:
        def locator = buildLayoutFactoryFor()

        and: "temporary tree created out of the Gradle build tree"
        def tmpDir = File.createTempFile("stop-", "-at").canonicalFile
        def stopAt = new File(tmpDir, 'stopAt')
        def currentDir = new File(new File(stopAt, "intermediate"), 'current')
        currentDir.mkdirs()

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        layout.buildDefinitionMissing

        cleanup: "temporary tree"
        tmpDir.deleteDir()
    }

    def "returns closest ancestor directory that contains a #settingsFilename file"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def subDir = tmpDir.createDir("sub")
        def settingsFile = subDir.createFile(settingsFilename)
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == subDir
        layout.settingsDir == subDir
        layout.settingsFile == settingsFile
        !layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "prefers the current directory as root directory with a #settingsFilename file"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def settingsFile = currentDir.createFile(settingsFilename)
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == settingsFile
        !layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns start directory when search upwards is disabled with a #settingsFilename file"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def layout = locator.getLayoutFor(currentDir, false)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns current directory when no settings or wrapper properties files"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")

        expect:
        def layout = locator.getLayoutFor(currentDir, tmpDir.testDirectory)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        layout.buildDefinitionMissing
    }

    def "can override build layout by specifying the settings file to #overrideSettingsFilename with existing #settingsFilename"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def rootDir = tmpDir.createDir("root")
        def settingsFile = rootDir.createFile(overrideSettingsFilename)
        def startParameter = new StartParameterInternal()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = settingsFile
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == rootDir
        layout.settingsDir == rootDir
        layout.settingsFile == settingsFile
        !layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
        overrideSettingsFilename = "some-$settingsFilename"
    }

    def "can override build layout by specifying an empty settings script with existing #settingsFilename"() {
        given:
        def locator = buildLayoutFactoryFor()

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def startParameter = new StartParameterInternal()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = null
        def config = new BuildLayoutConfiguration(startParameter)

        expect:
        def layout = locator.getLayoutFor(config)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == new File(currentDir, settingsFilename) // this is the current behaviour
        !layout.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    BuildLayoutFactory buildLayoutFactoryFor() {
        new BuildLayoutFactory()
    }
}
