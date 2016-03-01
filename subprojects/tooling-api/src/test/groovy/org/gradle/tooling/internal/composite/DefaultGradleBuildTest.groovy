/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.composite

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultGradleBuildTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = TestNameTestDirectoryProvider.newInstance()
    File projectDir
    DefaultGradleBuild gradleBuild

    def setup() {
        projectDir = testDirectoryProvider.createDir("projectDir")
        gradleBuild = new DefaultGradleBuild(projectDir, null, null, null)
    }

    def "create build identity from projectDir"() {
        expect:
        gradleBuild.toBuildIdentity().rootDir == projectDir
    }

    def "create project identity from projectDir and path"() {
        when:
        def projectIdentity = gradleBuild.toProjectIdentity(":mypath")
        then:
        projectIdentity.rootDir == projectDir
        projectIdentity.projectPath == ":mypath"
        projectIdentity.build.rootDir == projectDir
    }

    def "does not except illegal project paths when creating project identity"() {
        when:
        gradleBuild.toProjectIdentity(null)
        then:
        thrown(NullPointerException)
        when:
        gradleBuild.toProjectIdentity("relative:path")
        then:
        thrown(IllegalArgumentException)
    }
}
