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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppLibraryWithStaticLinkagePublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    def "can publish the binaries and headers of a library to a Maven repository"() {
        def lib = new CppLib()
        assert !lib.publicHeaders.files.empty
        assert !lib.privateHeaders.files.empty

        given:
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            library {
                baseName = 'test'
                linkage = [Linkage.STATIC]
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        lib.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(
            tasks.debug.allToCreate,
            tasks.release.allToCreate,
            ":generatePomFileForMainDebugPublication",
            ":generateMetadataFileForMainDebugPublication",
            ":publishMainDebugPublicationToMavenRepository",
            ":cppHeaders",
            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",
            ":generatePomFileForMainReleasePublication",
            ":generateMetadataFileForMainReleasePublication",
            ":publishMainReleasePublicationToMavenRepository",
            ":publish"
        )

        def headersZip = file("build/headers/cpp-api-headers.zip")
        new ZipTestFixture(headersZip).hasDescendants(lib.publicHeaders.files*.name)

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2-cpp-api-headers.zip", "test-1.2.pom", "test-1.2.module")
        main.artifactFile(classifier: 'cpp-api-headers', type: 'zip').assertIsCopyOf(headersZip)

        main.parsedPom.scopes.isEmpty()

        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 5
        def api = mainMetadata.variant("api")
        api.dependencies.empty
        api.files.size() == 1
        api.files[0].name == 'cpp-api-headers.zip'
        api.files[0].url == 'test-1.2-cpp-api-headers.zip'
        mainMetadata.variant("debugLink").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("debugRuntime").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("releaseLink").availableAt.coords == "some.group:test_release:1.2"
        mainMetadata.variant("releaseRuntime").availableAt.coords == "some.group:test_release:1.2"

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(withStaticLibrarySuffix("test_debug-1.2"), "test_debug-1.2.pom", "test_debug-1.2.module")
        debug.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/debug/test").file)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 2
        def debugLink = debugMetadata.variant('debugLink')
        debugLink.dependencies.empty
        debugLink.files.size() == 1
        debugLink.files[0].name == staticLibraryName('test')
        debugLink.files[0].url == withStaticLibrarySuffix("test_debug-1.2")
        def debugRuntime = debugMetadata.variant('debugRuntime')
        debugRuntime.dependencies.empty
        debugRuntime.files.empty

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(withStaticLibrarySuffix("test_release-1.2"), "test_release-1.2.pom", "test_release-1.2.module")
        release.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/release/test").file)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 2
        def releaseLink = releaseMetadata.variant('releaseLink')
        releaseLink.dependencies.empty
        releaseLink.files.size() == 1
        releaseLink.files[0].name == staticLibraryName('test')
        releaseLink.files[0].url == withStaticLibrarySuffix("test_release-1.2")
        def releaseRuntime = releaseMetadata.variant('releaseRuntime')
        releaseRuntime.dependencies.empty
        releaseRuntime.files.empty
    }

    def "correct variant of published library is selected when resolving"() {
        def app = new CppAppWithLibraryAndOptionalFeature()

        def repoDir = file("repo")
        def producer = file("greeting")
        producer.file("build.gradle") << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            publishing {
                repositories { maven { url '${repoDir.toURI()}' } }
            }

            library {
                linkage = [Linkage.STATIC]
                binaries.configureEach {
                    if (optimized) {
                        compileTask.get().macros(WITH_FEATURE: "true")
                    }
                }
            }
        """
        app.greeterLib.writeToProject(file(producer))

        executer.inDirectory(producer)
        run('publish')

        def consumer = file("consumer").createDir()
        consumer.file("settings.gradle") << ""
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-application'
            repositories { maven { url '${repoDir.toURI()}' } }
            dependencies { implementation 'some.group:greeting:1.2' }
            application {
                binaries.get { it.optimized }.configure {
                    compileTask.get().macros(WITH_FEATURE: "true")
                }
            }
        """
        app.main.writeToProject(consumer)

        when:
        executer.inDirectory(consumer)
        run("installDebug")

        then:
        def debugInstall = installation(consumer.file("build/install/main/debug"))
        debugInstall.exec().out == app.withFeatureDisabled().expectedOutput
        debugInstall.assertIncludesLibraries()

        when:
        executer.inDirectory(consumer)
        run("installRelease")

        then:
        def releaseInstall = installation(consumer.file("build/install/main/release"))
        releaseInstall.exec().out == app.withFeatureEnabled().expectedOutput
        releaseInstall.assertIncludesLibraries()
    }
}
