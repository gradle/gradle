/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.internal.SettingsInternal
import spock.lang.Specification

class ProjectSpecsTest extends Specification {
    static File buildFile
    static File projectDir
    static File currentDir

    def setupSpec() {
        projectDir = Mock(File)
        _ * projectDir.getCanonicalFile() >> projectDir
        currentDir = Mock(File)
        _ * currentDir.getCanonicalFile() >> currentDir
        buildFile = Mock(File)
        _ * buildFile.getCanonicalFile() >> buildFile
        _ * buildFile.getParent() >> currentDir

    }

    def "build file based spec"() {
        given:
        StartParameter parameter = new StartParameter()
        parameter.setBuildFile(buildFile)
        parameter.setProjectDir(projectDir)
        parameter.setCurrentDir(currentDir)

        expect:
        ProjectSpecs.forStartParameter(parameter, Stub(SettingsInternal)).class == BuildFileProjectSpec
    }

    def "project dir based spec"() {
        given:
        StartParameter parameter = new StartParameter()
        parameter.setProjectDir(projectDir)
        parameter.setCurrentDir(currentDir)

        expect:
        ProjectSpecs.forStartParameter(parameter, Stub(SettingsInternal)).class == ProjectDirectoryProjectSpec
    }

    def "current dir based spec"() {
        given:
        StartParameter parameter = new StartParameter()
        parameter.setCurrentDir(currentDir)

        expect:
        ProjectSpecs.forStartParameter(parameter, Stub(SettingsInternal)).class == CurrentDirectoryProjectSpec
    }
}
