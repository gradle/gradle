/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppLibraryPublishingIntegrationTest extends AbstractIntegrationSpec {
    def "can publish binaries and headers of a library to a maven repository"() {
        def lib = new CppLib()

        given:
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'
            
            group = 'my.group'
            version = '1.0-milestone-8a'
            library {
                baseName = 'mylib'
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        lib.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(":compileDebugCpp", ":linkDebug", ":generatePomFileForDebugPublication", ":publishDebugPublicationToMavenRepository", ":generatePomFileForMainPublication", ":publishMainPublicationToMavenRepository", ":compileReleaseCpp", ":linkRelease", ":generatePomFileForReleasePublication", ":publishReleasePublicationToMavenRepository", ":publish")

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('my.group', 'mylib', '1.0-milestone-8a')
        main.assertPublished()

        def debug = repo.module('my.group', 'mylib_debug', '1.0-milestone-8a')
        debug.assertPublished()
        debug.assertArtifactsPublished("mylib_debug-1.0-milestone-8a.dylib", "mylib_debug-1.0-milestone-8a.pom")
        debug.artifactFile(type: 'dylib').assertIsCopyOf(file("build/lib/main/debug/libmylib.dylib"))

        def release = repo.module('my.group', 'mylib_release', '1.0-milestone-8a')
        release.assertPublished()
        release.assertArtifactsPublished("mylib_release-1.0-milestone-8a.dylib", "mylib_release-1.0-milestone-8a.pom")
        release.artifactFile(type: 'dylib').assertIsCopyOf(file("build/lib/main/release/libmylib.dylib"))
    }
}
