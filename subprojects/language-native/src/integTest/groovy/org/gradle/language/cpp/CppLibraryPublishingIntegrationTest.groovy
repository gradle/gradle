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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppLibraryPublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can publish binaries and headers of a library to a maven repository"() {
        def lib = new CppLib()
        assert !lib.publicHeaders.files.empty

        given:
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'
            
            group = 'some.group'
            version = '1.2'
            library {
                baseName = 'test'
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        lib.publicHeaders.writeToSourceDir(file("src/main/public"))
        lib.privateHeaders.writeToProject(testDirectory)
        lib.sources.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(":compileDebugCpp", ":linkDebug", ":generatePomFileForDebugPublication", ":publishDebugPublicationToMavenRepository", ":cppHeaders", ":generatePomFileForMainPublication", ":publishMainPublicationToMavenRepository", ":compileReleaseCpp", ":linkRelease", ":generatePomFileForReleasePublication", ":publishReleasePublicationToMavenRepository", ":publish")

        def headersZip = file("build/headers/cpp-api-headers.zip")
        new ZipTestFixture(headersZip).hasDescendants(lib.publicHeaders.files*.name)

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2-cpp-api-headers.zip", "test-1.2.pom")
        main.artifactFile(classifier: 'cpp-api-headers', type: 'zip').assertIsCopyOf(headersZip)

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(withSharedLibrarySuffix("test_debug-1.2"), "test_debug-1.2.pom")
        debug.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/debug/test").file)

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(withSharedLibrarySuffix("test_release-1.2"), "test_release-1.2.pom")
        release.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/release/test").file)
    }
}
