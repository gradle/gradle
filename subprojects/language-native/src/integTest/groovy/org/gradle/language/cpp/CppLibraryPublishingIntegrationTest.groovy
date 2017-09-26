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

import org.gradle.nativeplatform.fixtures.AbstractNativePublishingIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.junit.Assume

class CppLibraryPublishingIntegrationTest extends AbstractNativePublishingIntegrationSpec implements CppTaskNames {

    def setup() {
        // TODO - currently the customizations to the tool chains are ignored by the plugins, so skip these tests until this is fixed
        Assume.assumeTrue(toolChain.id != "mingw" && toolChain.id != "gcccygwin")
    }

    def "can publish the binaries and headers of a library to a Maven repository"() {
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
        lib.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(*(compileAndLinkTasks(debug) + compileAndLinkTasks(release)), ":generatePomFileForDebugPublication", ":generateMetadataFileForDebugPublication", ":publishDebugPublicationToMavenRepository", ":cppHeaders", ":generatePomFileForMainPublication", ":generateMetadataFileForMainPublication", ":publishMainPublicationToMavenRepository", ":generatePomFileForReleasePublication", ":generateMetadataFileForReleasePublication", ":publishReleasePublicationToMavenRepository", ":publish")

        def headersZip = file("build/headers/cpp-api-headers.zip")
        new ZipTestFixture(headersZip).hasDescendants(lib.publicHeaders.files*.name)

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2-cpp-api-headers.zip", "test-1.2.pom", "test-1.2-module.json")
        main.artifactFile(classifier: 'cpp-api-headers', type: 'zip').assertIsCopyOf(headersZip)

        main.parsedPom.scopes.isEmpty()

        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 1
        mainMetadata.variant("cplusplus-api").files.size() == 1
        mainMetadata.variant("cplusplus-api").files[0].name == 'cpp-api-headers.zip'
        mainMetadata.variant("cplusplus-api").files[0].url == 'test-1.2-cpp-api-headers.zip'

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(withSharedLibrarySuffix("test_debug-1.2"), withLinkLibrarySuffix("test_debug-1.2"), "test_debug-1.2.pom", "test_debug-1.2-module.json")
        debug.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/debug/test").file)
        debug.artifactFile(type: linkLibrarySuffix).assertIsCopyOf(sharedLibrary("build/lib/main/debug/test").linkFile)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 2
        debugMetadata.variant('native-link').files.size() == 1
        debugMetadata.variant('native-link').files[0].name == linkLibraryName('test')
        debugMetadata.variant('native-link').files[0].url == withLinkLibrarySuffix("test_debug-1.2")
        debugMetadata.variant('native-runtime').files.size() == 1
        debugMetadata.variant('native-runtime').files[0].name == sharedLibraryName('test')
        debugMetadata.variant('native-runtime').files[0].url == withSharedLibrarySuffix("test_debug-1.2")

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(withSharedLibrarySuffix("test_release-1.2"), withLinkLibrarySuffix("test_release-1.2"), "test_release-1.2.pom", "test_release-1.2-module.json")
        release.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/release/test").file)
        release.artifactFile(type: linkLibrarySuffix).assertIsCopyOf(sharedLibrary("build/lib/main/release/test").linkFile)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 2
        releaseMetadata.variant('native-link').files.size() == 1
        releaseMetadata.variant('native-link').files[0].name == linkLibraryName('test')
        releaseMetadata.variant('native-link').files[0].url == withLinkLibrarySuffix("test_release-1.2")
        releaseMetadata.variant('native-runtime').files.size() == 1
        releaseMetadata.variant('native-runtime').files[0].name == sharedLibraryName('test')
        releaseMetadata.variant('native-runtime').files[0].url == withSharedLibrarySuffix("test_release-1.2")
    }

    def "can publish a library and its dependencies to a Maven repository"() {
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        def repoDir = file("repo")
        settingsFile << "include 'deck', 'card', 'shuffle'"
        buildFile << """
            subprojects {
                apply plugin: 'cpp-library'
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${repoDir.toURI()}' } }
                }
            }
            project(':deck') { 
                dependencies {
                    api project(':card')
                    implementation project(':shuffle')
                }
            }
"""
        app.deck.writeToProject(file('deck'))
        app.card.writeToProject(file('card'))
        app.shuffle.writeToProject(file('shuffle'))

        when:
        run('publish')

        then:
        def repo = new MavenFileRepository(repoDir)

        def deckModule = repo.module('some.group', 'deck', '1.2')
        deckModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2")
        deckModule.assertPublished()

        def deckDebugModule = repo.module('some.group', 'deck_debug', '1.2')
        deckDebugModule.assertPublished()
        deckDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2", "some.group:shuffle:1.2")

        def deckReleaseModule = repo.module('some.group', 'deck_release', '1.2')
        deckReleaseModule.assertPublished()
        deckReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2", "some.group:shuffle:1.2")

        def cardModule = repo.module('some.group', 'card', '1.2')
        cardModule.assertPublished()

        def cardDebugModule = repo.module('some.group', 'card_debug', '1.2')
        cardDebugModule.assertPublished()

        def cardReleaseModule = repo.module('some.group', 'card_release', '1.2')
        cardReleaseModule.assertPublished()

        def shuffleModule = repo.module('some.group', 'shuffle', '1.2')
        shuffleModule.assertPublished()

        def shuffleDebugModule = repo.module('some.group', 'shuffle_debug', '1.2')
        shuffleDebugModule.assertPublished()

        def shuffleReleaseModule = repo.module('some.group', 'shuffle_release', '1.2')
        shuffleReleaseModule.assertPublished()

        when:
        def consumer = file("consumer").createDir()
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-executable'
            repositories { maven { url '${repoDir.toURI()}' } }
            dependencies { implementation 'some.group:deck:1.2' }
"""
        app.main.writeToProject(consumer)

        executer.inDirectory(consumer)
        run("assemble")

        then:
        noExceptionThrown()
        sharedLibrary(consumer.file("build/install/main/debug/lib/deck")).file.assertExists()
        sharedLibrary(consumer.file("build/install/main/debug/lib/card")).file.assertExists()
        sharedLibrary(consumer.file("build/install/main/debug/lib/shuffle")).file.assertExists()
        installation(consumer.file("build/install/main/debug")).exec().out == app.expectedOutput
    }

    def "can publish a library with external dependencies to a Maven repository"() {
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        def repoDir = file("repo")
        def producer = file("producer")
        producer.file("settings.gradle") << "include 'card', 'shuffle'"
        producer.file("build.gradle") << """
            subprojects {
                apply plugin: 'cpp-library'
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${repoDir.toURI()}' } }
                }
            }
"""
        app.card.writeToProject(producer.file('card'))
        app.shuffle.writeToProject(producer.file('shuffle'))
        executer.inDirectory(producer)
        run('publish')

        settingsFile << "rootProject.name = 'deck'"
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'

            repositories { maven { url '${repoDir.toURI()}' } }
            
            group = 'some.group'
            version = '1.2'
            publishing {
                repositories { maven { url '${repoDir.toURI()}' } }
            }
            
            dependencies {
                api 'some.group:card:1.2'
                implementation 'some.group:shuffle:1.2'
            }
"""
        app.deck.writeToProject(testDirectory)

        when:
        run("publish")

        then:
        def repo = new MavenFileRepository(repoDir)

        def deckModule = repo.module('some.group', 'deck', '1.2')
        deckModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2")
        deckModule.assertPublished()

        def deckDebugModule = repo.module('some.group', 'deck_debug', '1.2')
        deckDebugModule.assertPublished()
        deckDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2", "some.group:shuffle:1.2")

        def deckReleaseModule = repo.module('some.group', 'deck_release', '1.2')
        deckReleaseModule.assertPublished()
        deckReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2", "some.group:shuffle:1.2")

        when:
        def consumer = file("consumer").createDir()
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-executable'
            repositories { maven { url '${repoDir.toURI()}' } }
            dependencies { implementation 'some.group:deck:1.2' }
"""
        app.main.writeToProject(consumer)

        executer.inDirectory(consumer)
        run("assemble")

        then:
        noExceptionThrown()
        sharedLibrary(consumer.file("build/install/main/debug/lib/deck")).file.assertExists()
        sharedLibrary(consumer.file("build/install/main/debug/lib/card")).file.assertExists()
        sharedLibrary(consumer.file("build/install/main/debug/lib/shuffle")).file.assertExists()
        installation(consumer.file("build/install/main/debug")).exec().out == app.expectedOutput
    }

}
