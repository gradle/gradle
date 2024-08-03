/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.fixtures.app.DuplicateAssemblerBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateMixedSameBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateWindowsResourcesBaseNamesTestApp
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

class DuplicateBaseNamesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    @RequiresInstalledToolChain(SUPPORTS_32)
    def "can have sourcefiles with same base name but different directories"() {
        given:
        def testApp = initTestApp(testAppType)

        when:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each { plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
model {
    platforms {
        x86 {
            architecture "i386"
        }
    }
    components {
        main(NativeExecutableSpec) {
            targetPlatform "x86"
            binaries.all {
                linker.args "-v"
            }
        }
    }
}
            """
        then:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == expectedOutput
        where:
        testAppType                                   | expectedOutput
        DuplicateCBaseNamesTestApp.simpleName         | "foo1foo2"
        DuplicateCppBaseNamesTestApp.simpleName       | "foo1foo2"
        DuplicateAssemblerBaseNamesTestApp.simpleName | "foo1foo2"
        DuplicateMixedSameBaseNamesTestApp.simpleName | "fooFromC\nfooFromCpp\nfooFromAsm\n"
    }

    private TestNativeComponent initTestApp(String testAppType) {
        switch (testAppType) {
            case DuplicateCBaseNamesTestApp.simpleName: return new DuplicateCBaseNamesTestApp()
            case DuplicateCppBaseNamesTestApp.simpleName: return new DuplicateCppBaseNamesTestApp()
            case DuplicateAssemblerBaseNamesTestApp.simpleName: return new DuplicateAssemblerBaseNamesTestApp(toolChain)
            case DuplicateMixedSameBaseNamesTestApp.simpleName: return new DuplicateMixedSameBaseNamesTestApp(toolChain)
            default: throw IllegalArgumentException(testAppType)
        }
    }

    /**
     * TODO: need filter declaration to get this passed. Remove filter once
     * story-language-source-sets-filter-source-files-by-file-extension
     * is implemented
     * */
    @RequiresInstalledToolChain(SUPPORTS_32)
    def "can have sourcefiles with same base name in same directory"() {
        setup:
        def testApp = new DuplicateMixedSameBaseNamesTestApp(AbstractInstalledToolChainIntegrationSpec.toolChain)


        testApp.getSourceFiles().each { SourceFile sourceFile ->
            file("src/main/all/${sourceFile.name}") << sourceFile.content
        }

        testApp.headerFiles.each { SourceFile sourceFile ->
            file("src/main/headers/${sourceFile.name}") << sourceFile.content
        }

        buildFile.text = ""
        testApp.plugins.each { plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
model {
    platforms {
        x86 {
            architecture "i386"
        }
    }
    components {
        main(NativeExecutableSpec) {
            targetPlatform "x86"
            binaries.all {
                linker.args "-v"
            }
            sources {"""

        testApp.functionalSourceSets.each { name, filterPattern ->
            buildFile << """
                $name {
                    source {
                        include '$filterPattern'
                        srcDirs "src/main/all"
                    }
                }"""
        }

        buildFile << """
            }
        }
    }
}
"""

        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == "fooFromC\nfooFromCpp\nfooFromAsm\n"
    }

    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    @Requires(UnitTestPreconditions.NotWindows)
    def "can have objectiveC and objectiveCpp source files with same name in different directories"() {
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each { plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }

        buildFile << """
model {
    ${testApp.extraConfiguration}
    components {
        main(NativeExecutableSpec)
    }
}
            """
        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == "foo1foo2"
        where:
        testApp << [new DuplicateObjectiveCBaseNamesTestApp(), new DuplicateObjectiveCppBaseNamesTestApp()]
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @ToBeFixedForConfigurationCache
    def "windows-resources can have sourcefiles with same base name but different directories"() {
        setup:
        def testApp = new DuplicateWindowsResourcesBaseNamesTestApp()
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each { plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                linker.args "user32.lib"
            }
        }
    }
}
            """
        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == "foo1foo2"
    }
}
