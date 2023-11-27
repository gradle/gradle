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

package org.gradle.language.swift

import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.junit.Assume

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        // Useful for diagnosing swiftc incremental compile failures
        buildFile << """
            allprojects {
                tasks.withType(SwiftCompile) {
                    compilerArgs.add('-driver-show-incremental')
                }
            }
        """
    }

    def 'recompiles only the Swift source files that have changed'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        def main = file("src/main/swift/main.swift").makeOlder()

        buildFile << """
            apply plugin: 'swift-application'
         """
        outputs.snapshot { succeeds("compileDebugSwift") }

        when:
        main.replace("a: 5, b: 7", "a: 21, b: 21")
        and:
        succeeds("assemble")

        then:
        outputs.recompiledFile(main)

        when:
        outputs.snapshot()
        main.replace("a: 21, b: 21", "a: 5, b: 7")
        succeeds("compileDebugSwift")

        then:
        outputs.recompiledFile(main)
    }

    def 'adding a new file only compiles new file'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        outputs.snapshot { succeeds("compileDebugSwift") }

        when:
        def newFile = file("src/main/swift/NewFile.swift")
        newFile << """
            public class NewFile {}
        """
        and:
        succeeds("compileDebugSwift")

        then:
        outputs.recompiledFile(newFile)
    }

    def 'adding a new file that overlaps with an existing type fails'() {
        given:
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        succeeds("compileDebugSwift")
        def newFile = file("src/main/swift/NewFile.swift")
        newFile.text = app.sum.sourceFile.content

        expect:
        fails("compileDebugSwift")
        failure.assertHasErrorOutput("error: invalid redeclaration of 'sum(a:b:)'")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4_OR_OLDER)
    @ToBeFixedForConfigurationCache
    def 'removing a file rebuilds everything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        outputs.snapshot { succeeds("compileDebugSwift") }
        file("src/main/swift/multiply.swift").delete()

        expect:
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter')
        outputs.deletedClasses("multiply")
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_5)
    def 'removing an isolated file does not rebuild anything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        outputs.snapshot { succeeds("compileDebugSwift") }
        file("src/main/swift/multiply.swift").delete()

        expect:
        succeeds("compileDebugSwift")
        outputs.recompiledClasses()
        outputs.deletedClasses("multiply")
    }

    def 'changing compiler arguments rebuilds everything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        outputs.snapshot { succeeds("compileDebugSwift") }

        buildFile << """
            tasks.withType(SwiftCompile) {
                compilerArgs.add('-Onone')
            }
        """

        expect:
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')
    }

    def 'changing macros rebuilds everything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        outputs.snapshot { succeeds("compileDebugSwift") }

        buildFile << """
            tasks.withType(SwiftCompile) {
                macros.add('NEWMACRO')
            }
        """

        expect:
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')
    }

    def 'changes to an unused dependency rebuilds everything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        createDirs("unused")
        settingsFile << """
            rootProject.name = 'app'
            include 'unused'
        """
        buildFile << """
            apply plugin: 'swift-application'

            project(":unused") {
                apply plugin: 'swift-library'
            }
        """
        file("unused/src/main/swift/Library.swift") << """
            public class Library {
                public init() {}
                public func unused() {
                }
            }
        """
        app.writeToProject(testDirectory)

        outputs.snapshot { succeeds("compileDebugSwift") }

        buildFile << """
            application {
                dependencies {
                    if (project.hasProperty("includeDep")) {
                        implementation(project(":unused"))
                    }
                }
            }
        """

        expect:
        // Addition of 'unused' dependency rebuilds everything.
        succeeds("compileDebugSwift", "-PincludeDep")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')

        and:
        // Removal of 'unused' dependency rebuilds everything.
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')
    }

    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    @ToBeFixedForConfigurationCache
    def 'changing Swift language level rebuilds everything'() {
        given:
        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
            application {
                if (project.hasProperty("swift4")) {
                    sourceCompatibility = SwiftVersion.SWIFT4
                } else {
                    sourceCompatibility = SwiftVersion.SWIFT3
                }
            }
         """

        outputs.snapshot { succeeds("compileDebugSwift") }

        expect:
        // rebuild for Swift4
        succeeds("compileDebugSwift", "-Pswift4")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')

        and:
        // rebuild for Swift3
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')
    }

    // This isn't quite right, we really want to assert something like "has both swiftc3 and swiftc4"
    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
    def 'changing Swift tool chain rebuilds everything'() {
        given:
        def swiftc3 = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC_3)
        def swiftc4 = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC_4)
        Assume.assumeNotNull(swiftc3, swiftc4)

        initScript.text = """
            allprojects { p ->
                apply plugin: ${swiftc3.pluginClass}

                model {
                      toolChains {
                        ${swiftc3.buildScriptConfig}
                      }
                }
            }
        """

        def outputs = new CompilationOutputsFixture(file("build/obj/main/debug"), [".o"])
        def app = new SwiftApp()
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """

        // Build with swiftc3
        outputs.snapshot { succeeds("compileDebugSwift") }

        initScript.text = """
            allprojects { p ->
                apply plugin: ${swiftc4.pluginClass}

                model {
                      toolChains {
                        ${swiftc4.buildScriptConfig}
                      }
                }
            }
        """

        expect:
        // rebuild with swiftc4
        succeeds("compileDebugSwift")
        outputs.recompiledClasses('main', 'sum', 'greeter', 'multiply')
    }
}
