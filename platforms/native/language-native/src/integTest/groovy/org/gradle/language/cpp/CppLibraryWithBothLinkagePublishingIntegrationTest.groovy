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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppLibraryWithBothLinkagePublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    @ToBeFixedForConfigurationCache
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
                linkage = [Linkage.STATIC, Linkage.SHARED]
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
            ':compileDebugSharedCpp', ':linkDebugShared',
            ':compileReleaseSharedCpp', ':linkReleaseShared', stripSymbolsTasks('ReleaseShared'),
            ':compileDebugStaticCpp', ':createDebugStatic',
            ':compileReleaseStaticCpp', ':createReleaseStatic',
            ":generatePomFileForMainDebugSharedPublication",
            ":generateMetadataFileForMainDebugSharedPublication",
            ":publishMainDebugSharedPublicationToMavenRepository",
            ":generatePomFileForMainDebugStaticPublication",
            ":generateMetadataFileForMainDebugStaticPublication",
            ":publishMainDebugStaticPublicationToMavenRepository",
            ":cppHeaders",
            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",
            ":generatePomFileForMainReleaseSharedPublication",
            ":generateMetadataFileForMainReleaseSharedPublication",
            ":publishMainReleaseSharedPublicationToMavenRepository",
            ":generatePomFileForMainReleaseStaticPublication",
            ":generateMetadataFileForMainReleaseStaticPublication",
            ":publishMainReleaseStaticPublicationToMavenRepository",
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
        mainMetadata.variants.size() == 9
        def api = mainMetadata.variant("api")
        api.dependencies.empty
        api.files.size() == 1
        api.files[0].name == 'cpp-api-headers.zip'
        api.files[0].url == 'test-1.2-cpp-api-headers.zip'
        mainMetadata.variant("debugSharedLink").availableAt.coords == "some.group:test_debug_shared:1.2"
        mainMetadata.variant("debugSharedRuntime").availableAt.coords == "some.group:test_debug_shared:1.2"
        mainMetadata.variant("debugStaticLink").availableAt.coords == "some.group:test_debug_static:1.2"
        mainMetadata.variant("debugStaticRuntime").availableAt.coords == "some.group:test_debug_static:1.2"
        mainMetadata.variant("releaseSharedLink").availableAt.coords == "some.group:test_release_shared:1.2"
        mainMetadata.variant("releaseSharedRuntime").availableAt.coords == "some.group:test_release_shared:1.2"
        mainMetadata.variant("releaseStaticLink").availableAt.coords == "some.group:test_release_static:1.2"
        mainMetadata.variant("releaseStaticRuntime").availableAt.coords == "some.group:test_release_static:1.2"

        def debugShared = repo.module('some.group', 'test_debug_shared', '1.2')
        debugShared.assertPublished()
        debugShared.assertArtifactsPublished(withSharedLibrarySuffix("test_debug_shared-1.2"), withLinkLibrarySuffix("test_debug_shared-1.2"), "test_debug_shared-1.2.pom", "test_debug_shared-1.2.module")
        debugShared.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/debug/shared/test").file)
        debugShared.artifactFile(type: linkLibrarySuffix).assertIsCopyOf(sharedLibrary("build/lib/main/debug/shared/test").linkFile)

        debugShared.parsedPom.scopes.isEmpty()

        def debugSharedMetadata = debugShared.parsedModuleMetadata
        debugSharedMetadata.variants.size() == 2
        debugSharedMetadata.variant('debugSharedLink')
        debugSharedMetadata.variant('debugSharedRuntime')

        def debugStatic = repo.module('some.group', 'test_debug_static', '1.2')
        debugStatic.assertPublished()
        debugStatic.assertArtifactsPublished(withStaticLibrarySuffix("test_debug_static-1.2"), "test_debug_static-1.2.pom", "test_debug_static-1.2.module")
        debugStatic.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/debug/static/test").file)

        debugStatic.parsedPom.scopes.isEmpty()

        def debugStaticMetadata = debugStatic.parsedModuleMetadata
        debugStaticMetadata.variants.size() == 2
        debugStaticMetadata.variant('debugStaticLink')
        debugStaticMetadata.variant('debugStaticRuntime')

        def releaseShared = repo.module('some.group', 'test_release_shared', '1.2')
        releaseShared.assertPublished()
        releaseShared.assertArtifactsPublished(withSharedLibrarySuffix("test_release_shared-1.2"), withLinkLibrarySuffix("test_release_shared-1.2"), "test_release_shared-1.2.pom", "test_release_shared-1.2.module")
        releaseShared.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/release/shared/test").strippedRuntimeFile)
        releaseShared.artifactFile(type: linkLibrarySuffix).assertIsCopyOf(sharedLibrary("build/lib/main/release/shared/test").strippedLinkFile)

        releaseShared.parsedPom.scopes.isEmpty()

        def releaseSharedMetadata = releaseShared.parsedModuleMetadata
        releaseSharedMetadata.variants.size() == 2
        releaseSharedMetadata.variant('releaseSharedLink')
        releaseSharedMetadata.variant('releaseSharedRuntime')

        def releaseStatic = repo.module('some.group', 'test_release_static', '1.2')
        releaseStatic.assertPublished()
        releaseStatic.assertArtifactsPublished(withStaticLibrarySuffix("test_release_static-1.2"), "test_release_static-1.2.pom", "test_release_static-1.2.module")
        releaseStatic.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/release/static/test").file)

        releaseStatic.parsedPom.scopes.isEmpty()

        def releaseStaticMetadata = releaseStatic.parsedModuleMetadata
        releaseStaticMetadata.variants.size() == 2
        releaseStaticMetadata.variant('releaseStaticLink')
        releaseStaticMetadata.variant('releaseStaticRuntime')
    }

    @ToBeFixedForConfigurationCache
    def "correct variant of published library is selected when resolving"() {
        def app = new CppAppWithLibraryAndOptionalFeature()

        def repoDir = file("repo")
        def producer = file("greeting")
        def producerSettings = producer.file("settings.gradle")
        producer.file("build.gradle") << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            publishing {
                repositories { maven { url '${repoDir.toURI()}' } }
            }

            library {
                linkage = [Linkage.STATIC, Linkage.SHARED]
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
        debugInstall.assertIncludesLibraries("greeting")
        def debugLib = sharedLibrary(producer.file("build/lib/main/debug/shared/greeting"))
        sharedLibrary(consumer.file("build/install/main/debug/lib/greeting")).file.assertIsCopyOf(debugLib.file)

        when:
        executer.inDirectory(consumer)
        run("installRelease")

        then:
        def releaseInstall = installation(consumer.file("build/install/main/release"))
        releaseInstall.exec().out == app.withFeatureEnabled().expectedOutput
        releaseInstall.assertIncludesLibraries("greeting")
        def releaseLib = sharedLibrary(producer.file("build/lib/main/release/shared/greeting"))
        sharedLibrary(consumer.file("build/install/main/release/lib/greeting")).file.assertIsCopyOf(releaseLib.strippedRuntimeFile)
    }
}
