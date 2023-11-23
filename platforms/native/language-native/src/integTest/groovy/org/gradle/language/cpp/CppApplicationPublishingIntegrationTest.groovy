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
import org.gradle.language.VariantContext
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.nativeplatform.MachineArchitecture.ARCHITECTURE_ATTRIBUTE
import static org.gradle.nativeplatform.MachineArchitecture.X86
import static org.gradle.nativeplatform.MachineArchitecture.X86_64
import static org.gradle.nativeplatform.OperatingSystemFamily.LINUX
import static org.gradle.nativeplatform.OperatingSystemFamily.MACOS
import static org.gradle.nativeplatform.OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE
import static org.gradle.nativeplatform.OperatingSystemFamily.WINDOWS

class CppApplicationPublishingIntegrationTest extends AbstractCppPublishingIntegrationTest implements CppTaskNames {
    def consumer = file("consumer").createDir()

    def setup() {
        consumer.file("settings.gradle").createFile()
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-application'

            repositories {
                maven {
                    url '${mavenRepo.uri}'
                }
            }
            configurations {
                install {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'native-runtime'))
                    attributes.attribute(Attribute.of('org.gradle.native.debuggable', Boolean), true)
                    attributes.attribute(Attribute.of('org.gradle.native.optimized', Boolean), false)
                }
            }
            // HACK to install the executable from a repository
            def binary = application.developmentBinary
            task install(type: InstallExecutable) {
                targetPlatform.set(binary.map { it.compileTask.get().targetPlatform.get() })
                toolChain.set(binary.map { it.toolChain })
                installDirectory = layout.projectDirectory.dir("install")
                lib(configurations.install.filter { it != configurations.install.files[0] })
                executableFile = layout.file(provider {
                    def appFile = configurations.install.files[0]
                    appFile.executable = true
                    appFile
                })
            }
        """
    }

    @ToBeFixedForConfigurationCache
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
                repositories { maven { url '$mavenRepo.uri' } }
            }
        """
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(
            tasks.debug.allToLink,
            tasks.release.allToLink,
            tasks.release.strip,
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

        def main = mavenRepo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2.pom", "test-1.2.module")
        main.parsedPom.scopes.isEmpty()
        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 2
        mainMetadata.variant("debugRuntime").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("releaseRuntime").availableAt.coords == "some.group:test_release:1.2"

        def debug = mavenRepo.module('some.group', 'test_debug', '1.2')
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

        def release = mavenRepo.module('some.group', 'test_release', '1.2')
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
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can publish an executable and library to a Maven repository"() {
        def app = new CppAppWithLibrary()

        given:
        createDirs("greeter", "app")
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'

                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${mavenRepo.uri}' } }
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
        def appModule = mavenRepo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        def appDebugModule = mavenRepo.module('some.group', 'app_debug', '1.2')
        appDebugModule.assertPublished()
        appDebugModule.parsedPom.scopes.size() == 1
        appDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 1
        appDebugRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        def appReleaseModule = mavenRepo.module('some.group', 'app_release', '1.2')
        appReleaseModule.assertPublished()
        appReleaseModule.parsedPom.scopes.size() == 1
        appReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        def greeterModule = mavenRepo.module('some.group', 'greeter', '1.2')
        greeterModule.assertPublished()

        def greeterDebugModule = mavenRepo.module('some.group', 'greeter_debug', '1.2')
        greeterDebugModule.assertPublished()

        def greeterReleaseModule = mavenRepo.module('some.group', 'greeter_release', '1.2')
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
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can publish an executable and a binary-specific dependency to a Maven repository"() {
        def app = new CppAppWithLibrary()
        def logger = new CppLogger().asLib()

        given:
        createDirs("greeter", "app", "logger")
        settingsFile << "include 'greeter', 'app', 'logger'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'

                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${mavenRepo.uri}' } }
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
        def appModule = mavenRepo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        def appDebugModule = mavenRepo.module('some.group', 'app_debug', '1.2')
        appDebugModule.assertPublished()
        appDebugModule.parsedPom.scopes.size() == 1
        appDebugModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2", "some.group:logger:1.2")

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 2
        appDebugRuntime.dependencies.collect { it.coords } == [ 'some.group:logger:1.2', 'some.group:greeter:1.2' ]

        def appReleaseModule = mavenRepo.module('some.group', 'app_release', '1.2')
        appReleaseModule.assertPublished()
        appReleaseModule.parsedPom.scopes.size() == 1
        appReleaseModule.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:greeter:1.2'

        mavenRepo.module('some.group', 'greeter', '1.2').assertPublished()
        mavenRepo.module('some.group', 'greeter_debug', '1.2').assertPublished()
        mavenRepo.module('some.group', 'greeter_release', '1.2').assertPublished()

        mavenRepo.module('some.group', 'logger', '1.2').assertPublished()
        mavenRepo.module('some.group', 'logger_debug', '1.2').assertPublished()
        mavenRepo.module('some.group', 'logger_release', '1.2').assertPublished()

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
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "uses the basename to calculate the coordinates"() {
        def app = new CppAppWithLibrary()

        given:
        createDirs("greeter", "app")
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'

                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${mavenRepo.uri}' } }
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
        def appModule = mavenRepo.module('some.group', 'testApp', '1.2')
        appModule.assertPublished()

        def appDebugModule = mavenRepo.module('some.group', 'testApp_debug', '1.2')
        appDebugModule.assertPublished()

        def appDebugMetadata = appDebugModule.parsedModuleMetadata
        def appDebugRuntime = appDebugMetadata.variant("debugRuntime")
        appDebugRuntime.dependencies.size() == 1
        appDebugRuntime.dependencies[0].coords == 'some.group:appGreeter:1.2'

        def appReleaseModule = mavenRepo.module('some.group', 'testApp_release', '1.2')
        appReleaseModule.assertPublished()

        def appReleaseMetadata = appReleaseModule.parsedModuleMetadata
        def appReleaseRuntime = appReleaseMetadata.variant("releaseRuntime")
        appReleaseRuntime.dependencies.size() == 1
        appReleaseRuntime.dependencies[0].coords == 'some.group:appGreeter:1.2'

        def greeterModule = mavenRepo.module('some.group', 'appGreeter', '1.2')
        greeterModule.assertPublished()

        def greeterDebugModule = mavenRepo.module('some.group', 'appGreeter_debug', '1.2')
        greeterDebugModule.assertPublished()

        def greeterReleaseModule = mavenRepo.module('some.group', 'appGreeter_release', '1.2')
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
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "can publish the binaries of an application with multiple target operating systems to a Maven repository"() {
        def app = new CppApp()
        def targetMachines = [machine(WINDOWS, X86), machine(LINUX, X86), machine(MACOS, X86_64)]

        given:
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            application {
                baseName = 'test'
                targetMachines = [machines.windows.x86, machines.linux.x86, machines.macOS.x86_64]
            }
            publishing {
                repositories { maven { url '$mavenRepo.uri' } }
            }
        """
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        assertMainModuleIsPublished('some.group', 'test', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'test', '1.2', ['debug', 'release'], targetMachines)

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
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    // macOS can only build 64-bit under 10.14+
    @Requires(UnitTestPreconditions.NotMacOs)
    @RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache
    def "can publish the binaries of an application with multiple target architectures to a Maven repository"() {
        def app = new CppApp()
        def targetMachines = [machine(currentOsFamilyName, X86), machine(currentOsFamilyName, X86_64)]

        given:
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'maven-publish'

            group = 'some.group'
            version = '1.2'
            application {
                baseName = 'test'
                targetMachines = [machines.os('${currentOsFamilyName}').x86, machines.os('${currentOsFamilyName}').x86_64]
            }
            publishing {
                repositories { maven { url '$mavenRepo.uri' } }
            }
        """
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        assertMainModuleIsPublished('some.group', 'test', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'test', '1.2', ['debug', 'release'], targetMachines)

        when:
        consumer.file("build.gradle") << """
            configurations {
                install {
                    attributes.attribute(Attribute.of('${OPERATING_SYSTEM_ATTRIBUTE}', OperatingSystemFamily), objects.named(OperatingSystemFamily, '${currentOsFamilyName}'))
                    attributes.attribute(Attribute.of('${ARCHITECTURE_ATTRIBUTE}', MachineArchitecture), objects.named(MachineArchitecture, '${X86}'))
                }
            }
            dependencies {
                install 'some.group:test:1.2'
            }
        """
        executer.inDirectory(consumer)
        run("install")

        then:
        def installation = installation("consumer/install")
        installation.exec().out == app.expectedOutput
    }

    @Override
    List<String> getLinkages() {
        return ['Runtime']
    }

    @Override
    List<String> getMainModuleArtifacts(String module, String version) {
        return ["${module}-${version}.pom", "${module}-${version}.module"]
    }

    @Override
    List<String> getVariantModuleArtifacts(String variantModuleNameWithVersion) {
        return [executableName(variantModuleNameWithVersion), "${variantModuleNameWithVersion}.pom", "${variantModuleNameWithVersion}.module"]
    }

    @Override
    TestFile getVariantSourceFile(String module, VariantContext variantContext) {
        def executable = executable("build/exe/main/${variantContext.asPath}${module}")
        return variantContext.buildType.name == 'release' ? executable.strippedRuntimeFile : executable.file
    }

    @Override
    Map<String, String> getVariantFileInformation(String linkage, String module, String variantModuleNameWithVersion) {
        return [ name: executableName(module), url: executableName(variantModuleNameWithVersion), extension: executableExtension ]
    }

    @Override
    int getVariantCount(List<Map<String, String>> targetMachines) {
        return 2 * linkages.size() * targetMachines.size()
    }

    @Override
    boolean publishesArtifactForLinkage(String linkage) {
        return linkage == 'Runtime'
    }
}
