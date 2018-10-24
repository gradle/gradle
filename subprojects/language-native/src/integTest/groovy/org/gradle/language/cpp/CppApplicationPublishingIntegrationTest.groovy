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

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppApplicationPublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {
    def repo = new MavenFileRepository(file("repo"))
    def consumer = file("consumer").createDir()

    def setup() {
        when:
        FeaturePreviewsFixture.enableGradleMetadata(consumer.file("settings.gradle"))
        consumer.file("build.gradle") << """
            repositories {
                maven { 
                    url '${repo.uri}' 
                }
            }
            configurations {
                install {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'native-runtime'))
                    attributes.attribute(Attribute.of('org.gradle.native.debuggable', Boolean), true)
                    attributes.attribute(Attribute.of('org.gradle.native.optimized', Boolean), false)
                }
            }
            task install(type: Sync) {
                from configurations.install
                into 'install'
            }
"""
    }

    def "can publish the binaries of an application to a Maven repository"() {
        def app = new CppApp()

        given:
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'maven-publish'
            
            group = 'some.group'
            version = '1.2'
            application {
                baseName = 'test'
            }
            publishing {
                repositories { maven { url '$repo.uri' } }
            }
"""
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(
            compileAndLinkTasks(debug),
            compileAndLinkTasks(release),
            stripSymbolsTasksRelease(),
            ":generatePomFileForMainDebugPublication",
            ":generateMetadataFileForMainDebugPublication",
            ":publishMainDebugPublicationToMavenRepository",
            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",
            ":generatePomFileForMainReleasePublication",
            ":generateMetadataFileForMainReleasePublication",
            ":publishMainReleasePublicationToMavenRepository",
            ":publish")

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2.pom", "test-1.2.module")
        main.parsedPom.scopes.isEmpty()
        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 2
        mainMetadata.variant("debugRuntime").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("releaseRuntime").availableAt.coords == "some.group:test_release:1.2"

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(executableName("test_debug-1.2"), "test_debug-1.2.pom", "test_debug-1.2.module")
        debug.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/debug/test").file)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 1
        def debugRuntime = debugMetadata.variant("debugRuntime")
        debugRuntime.dependencies.empty
        debugRuntime.files.size() == 1
        debugRuntime.files[0].name == executableName('test')
        debugRuntime.files[0].url == executableName("test_debug-1.2")

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(executableName("test_release-1.2"), "test_release-1.2.pom", "test_release-1.2.module")
        release.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/release/test").strippedRuntimeFile)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 1
        def releaseRuntime = releaseMetadata.variant("releaseRuntime")
        releaseRuntime.dependencies.empty
        releaseRuntime.files.size() == 1
        releaseRuntime.files[0].name == executableName('test')
        releaseRuntime.files[0].url == executableName("test_release-1.2")

        when:
        consumer.file("build.gradle") << """
            dependencies {
                install 'some.group:test:1.2'
            }
"""
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/test")
        executable.exec().out == app.expectedOutput
    }

    def "can publish an executable and library to a Maven repository"() {
        def app = new CppAppWithLibrary()

        given:
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${repo.uri}' } }
                }
            }
            project(':app') { 
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') { 
                apply plugin: 'cpp-library'
            }
        """
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        run('publish')

        then:
        def appModule = repo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        def appDebugModule = repo.module('some.group', 'app_debug', '1.2')
        appDebugModule.assertPublished()
        appDebugModule.parsedPom.scopes.size() == 1
        appDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 1
        appDebugRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        def appReleaseModule = repo.module('some.group', 'app_release', '1.2')
        appReleaseModule.assertPublished()
        appReleaseModule.parsedPom.scopes.size() == 1
        appReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        def greeterModule = repo.module('some.group', 'greeter', '1.2')
        greeterModule.assertPublished()

        def greeterDebugModule = repo.module('some.group', 'greeter_debug', '1.2')
        greeterDebugModule.assertPublished()

        def greeterReleaseModule = repo.module('some.group', 'greeter_release', '1.2')
        greeterReleaseModule.assertPublished()

        when:
        def consumer = file("consumer").createDir()
        consumer.file("settings.gradle") << ''
        consumer.file("build.gradle") << """
            dependencies {
                install 'some.group:app:1.2'
            }
        """
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/app")
        executable.exec().out == app.expectedOutput
    }

    def "can publish an executable and a binary-specific dependency to a Maven repository"() {
        def app = new CppAppWithLibrary()
        def logger = new CppLogger().asLib()

        given:
        settingsFile << "include 'greeter', 'app', 'logger'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${repo.uri}' } }
                }
            }
            project(':app') { 
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':greeter')
                }
                application {
                    binaries.configureEach {
                        dependencies {
                            if (!optimized) {
                                implementation project(':logger')
                            }
                        }
                    }
                }
            }
            project(':greeter') { 
                apply plugin: 'cpp-library'
            }
            project(':logger') {
                apply plugin: 'cpp-library'
            }
        """
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))
        logger.writeToProject(file('logger'))

        when:
        run('publish')

        then:
        def appModule = repo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        def appDebugModule = repo.module('some.group', 'app_debug', '1.2')
        appDebugModule.assertPublished()
        appDebugModule.parsedPom.scopes.size() == 1
        appDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2", "some.group:logger:1.2")

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 2
        appDebugRuntime.dependencies.collect { it.coords } == [ 'some.group:logger:1.2', 'some.group:greeter:1.2' ]

        def appReleaseModule = repo.module('some.group', 'app_release', '1.2')
        appReleaseModule.assertPublished()
        appReleaseModule.parsedPom.scopes.size() == 1
        appReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        repo.module('some.group', 'greeter', '1.2').assertPublished()
        repo.module('some.group', 'greeter_debug', '1.2').assertPublished()
        repo.module('some.group', 'greeter_release', '1.2').assertPublished()

        repo.module('some.group', 'logger', '1.2').assertPublished()
        repo.module('some.group', 'logger_debug', '1.2').assertPublished()
        repo.module('some.group', 'logger_release', '1.2').assertPublished()

        when:
        def consumer = file("consumer").createDir()
        consumer.file("settings.gradle") << ''
        consumer.file("build.gradle") << """
            dependencies {
                install 'some.group:app:1.2'
            }
        """
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/app")
        executable.exec().out == app.expectedOutput
    }

    def "uses the basename to calculate the coordinates"() {
        def app = new CppAppWithLibrary()

        given:
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${repo.uri}' } }
                }
            }
            project(':app') { 
                apply plugin: 'cpp-application'
                application.baseName = 'testApp'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') { 
                apply plugin: 'cpp-library'
                library.baseName = 'appGreeter'
            }
        """
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        run('publish')

        then:
        def appModule = repo.module('some.group', 'testApp', '1.2')
        appModule.assertPublished()

        def appDebugModule = repo.module('some.group', 'testApp_debug', '1.2')
        appDebugModule.assertPublished()

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 1
        appDebugRuntime.dependencies[0].coords == 'some.group:appGreeter:1.2'

        def appReleaseModule = repo.module('some.group', 'testApp_release', '1.2')
        appReleaseModule.assertPublished()

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:appGreeter:1.2'

        def greeterModule = repo.module('some.group', 'appGreeter', '1.2')
        greeterModule.assertPublished()

        def greeterDebugModule = repo.module('some.group', 'appGreeter_debug', '1.2')
        greeterDebugModule.assertPublished()

        def greeterReleaseModule = repo.module('some.group', 'appGreeter_release', '1.2')
        greeterReleaseModule.assertPublished()

        when:
        def consumer = file("consumer").createDir()
        consumer.file("settings.gradle") << ''
        consumer.file("build.gradle") << """
            dependencies {
                install 'some.group:testApp:1.2'
            }
"""
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/testApp")
        executable.exec().out == app.expectedOutput
    }

    def "can publish the binaries of an application with explicit operating system family support to a Maven repository"() {
        def app = new CppApp()

        given:
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            application {
                baseName = 'test'
                targetMachines = [machines.windows().x64(), machines.linux().x64(), machines.macos().x64()]
            }
            publishing {
                repositories { maven { url '$repo.uri' } }
            }
"""
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        def main = repo.module('some.group', 'test', '1.2')
        main.assertArtifactsPublished("test-1.2.pom", "test-1.2.module")
        main.parsedPom.scopes.isEmpty()
        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 6
        mainMetadata.variant("debugWindowsX86-64Runtime").availableAt.coords == "some.group:test_debug_windows_x86_64:1.2"
        mainMetadata.variant("releaseWindowsX86-64Runtime").availableAt.coords == "some.group:test_release_windows_x86_64:1.2"
        mainMetadata.variant("debugLinuxX86-64Runtime").availableAt.coords == "some.group:test_debug_linux_x86_64:1.2"
        mainMetadata.variant("releaseLinuxX86-64Runtime").availableAt.coords == "some.group:test_release_linux_x86_64:1.2"
        mainMetadata.variant("debugMacosX86-64Runtime").availableAt.coords == "some.group:test_debug_macos_x86_64:1.2"
        mainMetadata.variant("releaseMacosX86-64Runtime").availableAt.coords == "some.group:test_release_macos_x86_64:1.2"

        def debug = repo.module('some.group', "test_debug_${currentOsFamilyName}_x86_64", '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(executableName("test_debug_${currentOsFamilyName}_x86_64-1.2"), "test_debug_${currentOsFamilyName}_x86_64-1.2.pom", "test_debug_${currentOsFamilyName}_x86_64-1.2.module")
        debug.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/debug/${currentOsFamilyName}/x86-64/test").file)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 1
        def debugRuntime = debugMetadata.variant("debug${currentOsFamilyName.capitalize()}X86-64Runtime")
        debugRuntime.dependencies.empty
        debugRuntime.files.size() == 1
        debugRuntime.files[0].name == executableName('test')
        debugRuntime.files[0].url == executableName("test_debug_${currentOsFamilyName}_x86_64-1.2")

        def release = repo.module('some.group', "test_release_${currentOsFamilyName}_x86_64", '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(executableName("test_release_${currentOsFamilyName}_x86_64-1.2"), "test_release_${currentOsFamilyName}_x86_64-1.2.pom", "test_release_${currentOsFamilyName}_x86_64-1.2.module")
        release.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/release/${currentOsFamilyName}/x86-64/test").strippedRuntimeFile)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 1
        def releaseRuntime = releaseMetadata.variant("release${currentOsFamilyName.capitalize()}X86-64Runtime")
        releaseRuntime.dependencies.empty
        releaseRuntime.files.size() == 1
        releaseRuntime.files[0].name == executableName('test')
        releaseRuntime.files[0].url == executableName("test_release_${currentOsFamilyName}_x86_64-1.2")

        when:
        consumer.file("build.gradle") << """
            configurations {
                install {
                    attributes.attribute(Attribute.of('org.gradle.native.operatingSystem', OperatingSystemFamily), objects.named(OperatingSystemFamily, '${currentOsFamilyName}'))
                }
            }
            dependencies {
                install 'some.group:test:1.2'
            }
"""
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/test")
        executable.exec().out == app.expectedOutput
    }

    @Override
    ExecutableFixture executable(Object path) {
        ExecutableFixture executable = super.executable(path)
        // Executables synced from a binary repo lose their executable bit
        executable.file.setExecutable(true)
        executable
    }

}
