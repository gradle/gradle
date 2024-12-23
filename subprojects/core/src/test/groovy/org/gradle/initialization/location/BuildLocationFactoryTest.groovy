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
package org.gradle.initialization.location

import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.scripts.ScriptFileUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildLocationFactoryTest extends Specification {

    static final def TEST_CASES = ScriptFileUtil.getValidSettingsFileNames()

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "returns current directory when it contains a #settingsFilename file"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.testDirectory
        def settingsFile = currentDir.createFile(settingsFilename)

        expect:
        def location = locator.getLocationFor(currentDir, true)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == settingsFile
        !location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns current directory when no ancestor directory contains a settings file or build file"() {
        given:
        def locator = buildLocationFactoryFor()

        and: "temporary tree created out of the Gradle build tree"
        def tmpDir = File.createTempFile("stop-", "-at").canonicalFile
        def stopAt = new File(tmpDir, 'stopAt')
        def currentDir = new File(new File(stopAt, "intermediate"), 'current')
        currentDir.mkdirs()

        expect:
        def location = locator.getLocationFor(currentDir, true)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        location.buildDefinitionMissing

        cleanup: "temporary tree"
        tmpDir.deleteDir()
    }

    def "returns closest ancestor directory that contains a #settingsFilename file"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def subDir = tmpDir.createDir("sub")
        def settingsFile = subDir.createFile(settingsFilename)
        tmpDir.createFile(settingsFilename)

        expect:
        def location = locator.getLocationFor(currentDir, true)
        location.buildDefinitionDirectory == subDir
        location.settingsFile == settingsFile
        !location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "prefers the current directory as root directory with a #settingsFilename file"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        def settingsFile = currentDir.createFile(settingsFilename)
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def location = locator.getLocationFor(currentDir, true)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == settingsFile
        !location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns start directory when search upwards is disabled with a #settingsFilename file"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")
        tmpDir.createFile("sub/$settingsFilename")
        tmpDir.createFile(settingsFilename)

        expect:
        def location = locator.getLocationFor(currentDir, false)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    def "returns current directory when no settings or wrapper properties files"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("sub/current")

        expect:
        def location = locator.getLocationFor(currentDir, tmpDir.testDirectory)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == new File(currentDir, "settings.gradle") // this is the current behaviour
        location.buildDefinitionMissing
    }

    def "can override build layout by specifying the settings file to #overrideSettingsFilename with existing #settingsFilename"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def rootDir = tmpDir.createDir("root")
        def settingsFile = rootDir.createFile(overrideSettingsFilename)
        def startParameter = new StartParameterInternal()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = settingsFile
        def config = new BuildLocationConfiguration(startParameter)

        expect:
        def location = locator.getLocationFor(config)
        location.buildDefinitionDirectory == rootDir
        location.settingsFile == settingsFile
        !location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
        overrideSettingsFilename = "some-$settingsFilename"
    }

    def "can override build layout by specifying an empty settings script with existing #settingsFilename"() {
        given:
        def locator = buildLocationFactoryFor()

        and:
        def currentDir = tmpDir.createDir("current")
        currentDir.createFile(settingsFilename)
        def startParameter = new StartParameterInternal()
        startParameter.currentDir = currentDir
        startParameter.settingsFile = null
        def config = new BuildLocationConfiguration(startParameter)

        expect:
        def location = locator.getLocationFor(config)
        location.buildDefinitionDirectory == currentDir
        location.settingsFile == new File(currentDir, settingsFilename) // this is the current behaviour
        !location.buildDefinitionMissing

        where:
        settingsFilename << TEST_CASES
    }

    BuildLocationFactory buildLocationFactoryFor() {
        new BuildLocationFactory()
    }
}
