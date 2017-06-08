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
import org.gradle.language.fixtures.app.DuplicateAssemblerBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateMixedSameBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateObjectiveCppBaseNamesTestApp
import org.gradle.language.fixtures.app.DuplicateWindowsResourcesBaseNamesTestApp
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeLanguageRequirement
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.RequiresSupportedLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP

class DuplicateBaseNamesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    @RequiresSupportedLanguage([NativeLanguageRequirement.C, NativeLanguageRequirement.C_PLUS_PLUS, NativeLanguageRequirement.ASSEMBLY])
    def "can have sourcefiles with same base name but different directories"() {
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
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
        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == expectedOutput
        where:
        testApp                                              |   expectedOutput
        new DuplicateCBaseNamesTestApp()                     |    "foo1foo2"
        new DuplicateCppBaseNamesTestApp()                   |    "foo1foo2"
        new DuplicateAssemblerBaseNamesTestApp(toolChain)    |    "foo1foo2"
        new DuplicateMixedSameBaseNamesTestApp(toolChain)    |    "fooFromC\nfooFromCpp\nfooFromAsm\n"
    }

    /**
     * TODO: need filter declaration to get this passed. Remove filter once
     * story-language-source-sets-filter-source-files-by-file-extension
     * is implemented
     * */
    @RequiresSupportedLanguage([NativeLanguageRequirement.C, NativeLanguageRequirement.C_PLUS_PLUS, NativeLanguageRequirement.ASSEMBLY])
    def "can have sourcefiles with same base name in same directory"() {
        setup:
        def testApp = new DuplicateMixedSameBaseNamesTestApp(AbstractInstalledToolChainIntegrationSpec.toolChain)


        testApp.getSourceFiles().each {  SourceFile sourceFile ->
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

    @Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
    @RequiresSupportedLanguage([NativeLanguageRequirement.OBJECTIVE_C, NativeLanguageRequirement.OBJECTIVE_C_PLUS_PLUS])
    def "can have objectiveC and objectiveCpp source files with same name in different directories"(){
        setup:
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
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
        testApp << [ new DuplicateObjectiveCBaseNamesTestApp(), new DuplicateObjectiveCppBaseNamesTestApp() ]
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @RequiresSupportedLanguage(NativeLanguageRequirement.WINDOWS_RESOURCE)
    def "windows-resources can have sourcefiles with same base name but different directories"() {
        setup:
        def testApp = new DuplicateWindowsResourcesBaseNamesTestApp();
        testApp.writeSources(file("src/main"))
        buildFile.text = ""
        testApp.plugins.each{ plugin ->
            buildFile << "apply plugin: '$plugin'\n"
        }
        buildFile <<"""
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
