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

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class BuildLayoutFactoryTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    final BuildLayoutFactory locator = new BuildLayoutFactory()

    def "returns current directory when it contains a settings file"() {
        def currentDir = tmpDir.dir
        def settingsFile = currentDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == settingsFile
    }

    def "looks for sibling directory called 'master' that it contains a settings file"() {
        def currentDir = tmpDir.createDir("current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        layout.settingsFile == settingsFile
    }

    def "searches ancestors for a directory called 'master' that contains a settings file"() {
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("master")
        def settingsFile = masterDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == masterDir.parentFile
        layout.settingsDir == masterDir
        layout.settingsFile == settingsFile
    }

    def "ignores 'master' directory when it does not contain a settings file"() {
        def currentDir = tmpDir.createDir("sub/current")
        def masterDir = tmpDir.createDir("sub/master")
        masterDir.createFile("gradle.properties")
        def settingsFile = tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, true)
        layout.rootDirectory == tmpDir.dir
        layout.settingsDir == tmpDir.dir
        layout.settingsFile == settingsFile
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
        layout.settingsFile == settingsFile
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
        layout.settingsFile == settingsFile
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
        layout.settingsFile == settingsFile
    }

    def "returns start directory when search upwards is disabled"() {
        def currentDir = tmpDir.createDir("sub/current")
        tmpDir.createFile("sub/settings.gradle")
        tmpDir.createFile("settings.gradle")

        expect:
        def layout = locator.getLayoutFor(currentDir, false)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == null
    }

    def "returns current directory when no settings or wrapper properties files found"() {
        def currentDir = tmpDir.createDir("sub/current")

        expect:
        def layout = locator.getLayoutFor(currentDir, tmpDir.dir)
        layout.rootDirectory == currentDir
        layout.settingsDir == currentDir
        layout.settingsFile == null
    }
}
