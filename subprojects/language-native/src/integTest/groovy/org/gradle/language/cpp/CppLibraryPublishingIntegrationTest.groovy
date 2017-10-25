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

import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraryAndOptionalFeature
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppLibraryPublishingIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements CppTaskNames {

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
        result.assertTasksExecuted(
            compileAndLinkTasks(debug),
            compileAndCreateTasks(debug),
            compileAndLinkTasks(release),
            compileAndCreateTasks(release),
            publishTasks(debug),
            publishTasks(release),
            publishTasks('', debug, staticLinkage),
            publishTasks('', release, staticLinkage),

            ":cppHeaders",

            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",

            ":publish"
        )

        def headersZip = file("build/headers/cpp-api-headers.zip")
        new ZipTestFixture(headersZip).hasDescendants(lib.publicHeaders.files*.name)

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2-cpp-api-headers.zip", "test-1.2.pom", "test-1.2-module.json")
        main.artifactFile(classifier: 'cpp-api-headers', type: 'zip').assertIsCopyOf(headersZip)

        main.parsedPom.scopes.isEmpty()

        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 9
        def api = mainMetadata.variant("api")
        api.dependencies.empty
        api.files.size() == 1
        api.files[0].name == 'cpp-api-headers.zip'
        api.files[0].url == 'test-1.2-cpp-api-headers.zip'
        mainMetadata.variant("debugShared-link").availableAt.coords == "some.group:test_debugShared:1.2"
        mainMetadata.variant("debugShared-runtime").availableAt.coords == "some.group:test_debugShared:1.2"
        mainMetadata.variant("releaseShared-link").availableAt.coords == "some.group:test_releaseShared:1.2"
        mainMetadata.variant("releaseShared-runtime").availableAt.coords == "some.group:test_releaseShared:1.2"

        assertSharedLibraryPublished(repo, 'debug')
        assertSharedLibraryPublished(repo, 'release')
        assertStaticLibraryPublished(repo, 'debug')
        assertStaticLibraryPublished(repo, 'release')
    }

    private void assertSharedLibraryPublished(MavenFileRepository repo, String buildType) {
        def variant = "${buildType}Shared"
        def module = repo.module('some.group', "test_$variant", '1.2')
        module.assertPublished()
        module.assertArtifactsPublished(withSharedLibrarySuffix("test_$variant-1.2"), withLinkLibrarySuffix("test_$variant-1.2"), "test_$variant-1.2.pom", "test_$variant-1.2-module.json")
        module.artifactFile(type: sharedLibraryExtension).assertIsCopyOf(sharedLibrary("build/lib/main/$buildType/shared/test").file)
        module.artifactFile(type: linkLibrarySuffix).assertIsCopyOf(sharedLibrary("build/lib/main/$buildType/shared/test").linkFile)

        assert module.parsedPom.scopes.isEmpty()

        def metadata = module.parsedModuleMetadata
        assert metadata.variants.size() == 2

        def link = metadata.variant("${variant}-link")
        assert link.dependencies.empty
        assert link.files.size() == 1
        assert link.files[0].name == linkLibraryName('test')
        assert link.files[0].url == withLinkLibrarySuffix("test_$variant-1.2")

        def runtime = metadata.variant("${variant}-runtime")
        assert runtime.dependencies.empty
        assert runtime.files.size() == 1
        assert runtime.files[0].name == sharedLibraryName('test')
        assert runtime.files[0].url == withSharedLibrarySuffix("test_$variant-1.2")
    }

    private void assertStaticLibraryPublished(MavenFileRepository repo, String buildType) {
        def variant = "${buildType}Static"
        def module = repo.module('some.group', "test_$variant", '1.2')
        module.assertPublished()
        module.assertArtifactsPublished(withStaticLibrarySuffix("test_$variant-1.2"), "test_$variant-1.2.pom", "test_$variant-1.2-module.json")
        module.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/$buildType/static/test").file)

        assert module.parsedPom.scopes.isEmpty()

        def metadata = module.parsedModuleMetadata
        assert metadata.variants.size() == 2

        def link = metadata.variant("${variant}-link")
        assert link.dependencies.empty
        assert link.files.size() == 1
        assert link.files[0].name == staticLibraryName('test')
        assert link.files[0].url == withStaticLibrarySuffix("test_$variant-1.2")

        def runtime = metadata.variant("${variant}-runtime")
        assert runtime.dependencies.empty
        assert runtime.files.empty
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
        assertDeckModuleVariantPublished(repo)
        assertModulesArePublishedWithNoDependencies(repo, 'card')
        assertModulesArePublishedWithNoDependencies(repo, 'shuffle')

        when:
        def consumer = file("consumer").createDir()
        consumer.file('settings.gradle') << ''
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-executable'
            repositories { maven { url '${repoDir.toURI()}' } }
            dependencies { implementation 'some.group:deck:1.2' }
"""
        app.main.writeToProject(consumer)

        executer.inDirectory(consumer)
        run("dependencies", "assemble", "installDebugStatic")

        then:
        noExceptionThrown()
        installation(consumer.file("build/install/main/debug/shared")).assertIncludesLibraries("deck", "card", "shuffle")
        installation(consumer.file("build/install/main/debug/shared")).exec().out == app.expectedOutput

        installation(consumer.file("build/install/main/debug/static")).assertIncludesLibraries()
        installation(consumer.file("build/install/main/debug/static")).exec().out == app.expectedOutput
    }

    private void assertDeckModuleVariantPublished(MavenFileRepository repo) {
        def deckModule = repo.module('some.group', 'deck', '1.2')
        deckModule.assertPublished()
        assert deckModule.parsedPom.scopes.size() == 1
        deckModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2")

        def deckMetadata = deckModule.parsedModuleMetadata
        def deckApi = deckMetadata.variant("api")
        assert deckApi.dependencies.size() == 1
        assert deckApi.dependencies[0].group == "some.group"
        assert deckApi.dependencies[0].module == "card"
        assert deckApi.dependencies[0].version == "1.2"

        assertDeckModuleVariantPublished(repo, 'debugShared')
        assertDeckModuleVariantPublished(repo, 'releaseShared')
        assertDeckModuleVariantPublished(repo, 'debugStatic')
        assertDeckModuleVariantPublished(repo, 'releaseStatic')
    }

    private void assertModulesArePublishedWithNoDependencies(MavenFileRepository repo, String artifactId) {
        def module = repo.module('some.group', artifactId, '1.2')
        module.assertPublished()
        assert module.parsedPom.scopes.isEmpty()

        def debugSharedModule = repo.module('some.group', "${artifactId}_debugShared", '1.2')
        debugSharedModule.assertPublished()
        assert debugSharedModule.parsedPom.scopes.isEmpty()

        def releaseSharedModule = repo.module('some.group', "${artifactId}_releaseShared", '1.2')
        releaseSharedModule.assertPublished()
        assert releaseSharedModule.parsedPom.scopes.isEmpty()

        def debugStaticModule = repo.module('some.group', "${artifactId}_debugStatic", '1.2')
        debugStaticModule.assertPublished()
        assert debugStaticModule.parsedPom.scopes.isEmpty()

        def releaseStaticModule = repo.module('some.group', "${artifactId}_releaseStatic", '1.2')
        releaseStaticModule.assertPublished()
        assert releaseStaticModule.parsedPom.scopes.isEmpty()
    }

    private void assertDeckModuleVariantPublished(MavenFileRepository repo, String variant) {
        def module = repo.module('some.group', "deck_${variant}", '1.2')
        module.assertPublished()
        assert module.parsedPom.scopes.size() == 1
        module.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2", "some.group:shuffle:1.2")

        def metadata = module.parsedModuleMetadata
        def link = metadata.variant("${variant}-link")
        assert link.dependencies.size() == 2
        assert link.dependencies[0].group == "some.group"
        assert link.dependencies[0].module == "shuffle"
        assert link.dependencies[0].version == "1.2"
        assert link.dependencies[1].group == "some.group"
        assert link.dependencies[1].module == "card"
        assert link.dependencies[1].version == "1.2"

        def runtime = metadata.variant("${variant}-runtime")
        assert runtime.dependencies.size() == 2
        assert runtime.dependencies[0].group == "some.group"
        assert runtime.dependencies[0].module == "shuffle"
        assert runtime.dependencies[0].version == "1.2"
        assert runtime.dependencies[1].group == "some.group"
        assert runtime.dependencies[1].module == "card"
        assert runtime.dependencies[1].version == "1.2"
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
        deckModule.assertPublished()
        deckModule.parsedPom.scopes.size() == 1
        deckModule.parsedPom.scopes.runtime.assertDependsOn("some.group:card:1.2")

        def deckMetadata = deckModule.parsedModuleMetadata
        def deckApi = deckMetadata.variant("api")
        deckApi.dependencies.size() == 1
        deckApi.dependencies[0].group == "some.group"
        deckApi.dependencies[0].module == "card"
        deckApi.dependencies[0].version == "1.2"

        assertDeckModuleVariantPublished(repo, 'debugShared')
        assertDeckModuleVariantPublished(repo, 'releaseShared')
        assertDeckModuleVariantPublished(repo, 'debugStatic')
        assertDeckModuleVariantPublished(repo, 'releaseStatic')

        when:
        def consumer = file("consumer").createDir()
        consumer.file('settings.gradle') << ''
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
        installation(consumer.file("build/install/main/debug/shared")).assertIncludesLibraries("deck", "card", "shuffle")
        installation(consumer.file("build/install/main/debug/shared")).exec().out == app.expectedOutput
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
            compileReleaseSharedCpp.macros(WITH_FEATURE: "true")
            compileReleaseStaticCpp.macros(WITH_FEATURE: "true")
            
        """
        app.greeterLib.writeToProject(file(producer))

        executer.inDirectory(producer)
        run('publish')

        def consumer = file("consumer").createDir()
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-executable'
            repositories { maven { url '${repoDir.toURI()}' } }
            dependencies { implementation 'some.group:greeting:1.2' }
            compileReleaseSharedCpp.macros(WITH_FEATURE: "true")
            compileReleaseStaticCpp.macros(WITH_FEATURE: "true")
"""
        app.main.writeToProject(consumer)

        when:
        executer.inDirectory(consumer)
        run("installDebugShared")

        then:
        installation(consumer.file("build/install/main/debug/shared")).exec().out == app.withFeatureDisabled().expectedOutput

        when:
        executer.inDirectory(consumer)
        run("installReleaseShared")

        then:
        installation(consumer.file("build/install/main/release/shared")).exec().out == app.withFeatureEnabled().expectedOutput

        when:
        executer.inDirectory(consumer)
        run("installDebugStatic")

        then:
        installation(consumer.file("build/install/main/debug/static")).assertIncludesLibraries()
        installation(consumer.file("build/install/main/debug/static")).exec().out == app.withFeatureDisabled().expectedOutput

        when:
        executer.inDirectory(consumer)
        run("installReleaseStatic")

        then:
        installation(consumer.file("build/install/main/release/static")).assertIncludesLibraries()
        installation(consumer.file("build/install/main/release/static")).exec().out == app.withFeatureEnabled().expectedOutput
    }

}
