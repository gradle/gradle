/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.nativeplatform.MachineArchitecture.X86
import static org.gradle.nativeplatform.MachineArchitecture.X86_64
import static org.gradle.nativeplatform.OperatingSystemFamily.LINUX
import static org.gradle.nativeplatform.OperatingSystemFamily.MACOS
import static org.gradle.nativeplatform.OperatingSystemFamily.WINDOWS

class CppStaticLibraryPublishingIntegrationTest extends AbstractCppPublishingIntegrationTest {
    @ToBeFixedForConfigurationCache
    def "can publish a library and its dependencies to a Maven repository when multiple target operating systems are specified"() {
        def app = new CppAppWithLibrariesWithApiDependencies()
        def targetMachines = [machine(WINDOWS, currentArchitecture), machine(LINUX, currentArchitecture), machine(MACOS, currentArchitecture)]

        given:
        createDirs("deck", "card", "shuffle")
        settingsFile << "include 'deck', 'card', 'shuffle'"
        buildFile << """
            subprojects {
                apply plugin: 'cpp-library'
                apply plugin: 'maven-publish'

                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${mavenRepo.uri}' } }
                }

                components.withType(CppComponent) {
                    linkage = [Linkage.STATIC]
                    targetMachines = [machines.windows.architecture('${currentArchitecture}'), machines.linux.architecture('${currentArchitecture}'), machines.macOS.architecture('${currentArchitecture}')]
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
        assertMainModuleIsPublished('some.group', 'deck', '1.2', targetMachines, ["some.group:card:1.2"])
        assertVariantsArePublished('some.group', 'deck', '1.2', ['debug', 'release'], targetMachines, ["some.group:shuffle:1.2", "some.group:card:1.2"])

        and:
        assertMainModuleIsPublished('some.group', 'card', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'card', '1.2', ['debug', 'release'], targetMachines)

        and:
        assertMainModuleIsPublished('some.group', 'shuffle', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'shuffle', '1.2', ['debug', 'release'], targetMachines)

        when:
        def consumer = file("consumer").createDir()
        consumer.file('settings.gradle') << ''
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-application'
            repositories { maven { url '${mavenRepo.uri}' } }
            dependencies { implementation 'some.group:deck:1.2' }
        """
        app.main.writeToProject(consumer)

        executer.inDirectory(consumer)
        run("assemble")

        then:
        noExceptionThrown()
        installation(consumer.file("build/install/main/debug")).exec().out == app.expectedOutput
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache
    def "can publish a library and its dependencies to a Maven repository when multiple target architectures are specified"() {
        def app = new CppAppWithLibrariesWithApiDependencies()
        def targetMachines = [machine(currentOsFamilyName, X86), machine(currentOsFamilyName, X86_64)]

        given:
        createDirs("deck", "card", "shuffle")
        settingsFile << "include 'deck', 'card', 'shuffle'"
        buildFile << """
            subprojects {
                apply plugin: 'cpp-library'
                apply plugin: 'maven-publish'

                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '${mavenRepo.uri}' } }
                }

                components.withType(CppComponent) {
                    linkage = [Linkage.STATIC]
                    targetMachines = [machines.host().x86, machines.host().x86_64]
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
        assertMainModuleIsPublished('some.group', 'deck', '1.2', targetMachines, ["some.group:card:1.2"])
        assertVariantsArePublished('some.group', 'deck', '1.2', ['debug', 'release'], targetMachines, ["some.group:shuffle:1.2", "some.group:card:1.2"])

        and:
        assertMainModuleIsPublished('some.group', 'card', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'card', '1.2', ['debug', 'release'], targetMachines)

        and:
        assertMainModuleIsPublished('some.group', 'shuffle', '1.2', targetMachines)
        assertVariantsArePublished('some.group', 'shuffle', '1.2', ['debug', 'release'], targetMachines)

        when:
        def consumer = file("consumer").createDir()
        consumer.file('settings.gradle') << ''
        consumer.file("build.gradle") << """
            apply plugin: 'cpp-application'
            repositories { maven { url '${mavenRepo.uri}' } }
            dependencies { implementation 'some.group:deck:1.2' }
        """
        app.main.writeToProject(consumer)

        executer.inDirectory(consumer)
        run("assemble")

        then:
        noExceptionThrown()
        installation(consumer.file("build/install/main/debug")).exec().out == app.expectedOutput
    }

    @Override
    int getVariantCount(List<Map<String, String>> targetMachines) {
        return 2 * linkages.size() * targetMachines.size() + 1
    }

    @Override
    List<String> getLinkages() {
        return ['Link', 'Runtime']
    }

    @Override
    List<String> getMainModuleArtifacts(String module, String version) {
        return  ["${module}-${version}.pom", "${module}-${version}.module", "${module}-${version}-cpp-api-headers.zip"]
    }

    @Override
    List<String> getVariantModuleArtifacts(String variantModuleNameWithVersion) {
        return [withStaticLibrarySuffix(variantModuleNameWithVersion), "${variantModuleNameWithVersion}.pom", "${variantModuleNameWithVersion}.module"]
    }

    @Override
    TestFile getVariantSourceFile(String module, VariantContext variantContext) {
        return staticLibrary("${module}/build/lib/main/${variantContext.asPath}${module}").file
    }

    @Override
    Map<String, String> getVariantFileInformation(String linkage, String module, String variantModuleNameWithVersion) {
        return [name: staticLibraryName(module), url: withStaticLibrarySuffix(variantModuleNameWithVersion), extension: staticLibraryExtension]
    }

    @Override
    boolean publishesArtifactForLinkage(String linkage) {
        return linkage == 'Link'
    }
}
