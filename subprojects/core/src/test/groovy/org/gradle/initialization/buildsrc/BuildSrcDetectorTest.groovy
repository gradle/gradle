/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc


import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static BuildSrcDetector.isValidBuildSrcBuild
import static org.gradle.internal.scripts.ScriptFileUtil.getValidBuildFileNames
import static org.gradle.internal.scripts.ScriptFileUtil.getValidSettingsFileNames

class BuildSrcDetectorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "ignores buildSrc file that is not a directory"() {
        expect:
        !isValidBuildSrcBuild(temp.file("buildSrc"))
        !isValidBuildSrcBuild(temp.createFile("buildSrc"))
    }

    def "ignores empty buildSrc directory"() {
        expect:
        !isValidBuildSrcBuild(temp.createDir("buildSrc"))
    }

    def "ignores buildSrc directory without valid Gradle build"() {
        when:
        def buildSrcDir = temp.createDir("buildSrc")
        buildSrcDir.createFile("build/libs/buildSrc.jar")
        buildSrcDir.createFile(".gitattributes")
        buildSrcDir.createFile("test.txt")

        then:
        !isValidBuildSrcBuild(buildSrcDir)
    }

    def "ignores buildSrc directory with empty src directory"() {
        when:
        def buildSrcDir = temp.createDir("buildSrc")
        buildSrcDir.createDir("src/main/java")

        then:
        !isValidBuildSrcBuild(buildSrcDir)
    }

    def "does not ignore buildSrc directory with src directory containing source file"() {
        when:
        def buildSrcDir = temp.createDir("buildSrc")
        buildSrcDir.createFile("src/main/java/Dummy.java")

        then:
        isValidBuildSrcBuild(buildSrcDir)
    }

    def "does not ignore buildSrc directory with #fileName file"() {
        when:
        def buildSrcDir = temp.createDir("buildSrc")
        buildSrcDir.createFile(fileName)

        then:
        isValidBuildSrcBuild(buildSrcDir)

        where:
        fileName << getValidSettingsFileNames() + getValidBuildFileNames()
    }
}
