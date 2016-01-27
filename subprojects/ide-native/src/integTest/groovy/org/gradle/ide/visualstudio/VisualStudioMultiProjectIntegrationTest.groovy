/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio

import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp

class VisualStudioMultiProjectIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    private final Set<String> projectConfigurations = ['debug', 'release'] as Set

    def app = new CppHelloWorldApp()

    def setup() {
        buildFile << """
            subprojects {
                apply plugin: 'cpp'
                apply plugin: 'visual-studio'

                model {
                    platforms {
                        win32 {
                            architecture "i386"
                        }
                    }
                    buildTypes {
                        debug
                        release
                    }
                }
            }
        """
    }

    def "create visual studio solution for executable that depends on static library in another project"() {
        when:
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        settingsFile.text = "include ':exe', ':lib'"
        file("exe", "build.gradle") << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib project: ':lib', library: 'hello', linkage: 'static'
            }
        }
    }
}
"""
        file("lib", "build.gradle") << """
model {
    components {
        hello(NativeLibrarySpec)
    }
}
"""
        and:
        run ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.assertHasComponentSources(app.executable, "src/main")
        exeProject.projectConfigurations.keySet() == projectConfigurations
        exeProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/main/headers", "../lib/src/hello/headers")
            assert it.buildCommand == "gradle -p \"..\" :exe:installMain${it.name.capitalize()}Executable"
        }

        and:
        final libProject = projectFile("lib/lib_helloLib.vcxproj")
        libProject.assertHasComponentSources(app.library, "src/hello")
        libProject.projectConfigurations.keySet() == projectConfigurations
        libProject.projectConfigurations.values().each {
            assert it.includePath == filePath("src/hello/headers")
            assert it.buildCommand == "gradle -p \"..\" :lib:hello${it.name.capitalize()}StaticLibrary"
        }

        and:
        final mainSolution = solutionFile("exe/exe_mainExe.sln")
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(libProject, projectConfigurations)
    }

    def "create visual studio solution for executable that transitively depends on multiple projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("greet/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib', ':greet'"
        buildFile << """
project(":exe") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
        }
    }
}
project(":lib") {
    apply plugin: "cpp"
    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp.lib project: ':greet', library: 'greetings', linkage: 'static'
                }
            }
        }
    }
}
project(":greet") {
    apply plugin: "cpp"
    model {
        components {
            greetings(NativeLibrarySpec)
        }
    }
}
"""

        when:
        succeeds ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloProject = projectFile("lib/lib_helloDll.vcxproj")
        final greetProject = projectFile("greet/greet_greetingsLib.vcxproj")
        final mainSolution = solutionFile("exe/exe_mainExe.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "greet_greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/hello/headers")
        helloProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../greet/src/greetings/headers")
        greetProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
    }

    def "create visual studio solution where multiple components have same name"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/main"), file("greet/src/main"))

        and:
        settingsFile.text = "include ':exe', ':lib', ':greet'"
        buildFile << """
project(":exe") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'main'
                }
            }
        }
    }
}
project(":lib") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeLibrarySpec) {
                sources {
                    cpp.lib project: ':greet', library: 'main', linkage: 'static'
                }
            }
        }
    }
}
project(":greet") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeLibrarySpec)
        }
    }
}
"""

        when:
        succeeds ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloProject = projectFile("lib/lib_mainDll.vcxproj")
        final greetProject = projectFile("greet/greet_mainLib.vcxproj")
        final mainSolution = solutionFile("exe/exe_mainExe.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_mainDll", "greet_mainLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/main/headers")
        helloProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../greet/src/main/headers")
        greetProject.projectConfigurations['debug'].includePath == filePath("src/main/headers")
    }

    def "create visual studio solution for executable with project dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("exe/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
project(":exe") {
    apply plugin: "cpp"
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'hello'
                }
            }
            greetings(NativeLibrarySpec)
        }
    }
}
project(":lib") {
    apply plugin: "cpp"
    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp.lib project: ':exe', library: 'greetings', linkage: 'static'
                }
            }
        }
    }
}
"""

        when:
        succeeds ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        final helloProject = projectFile("lib/lib_helloDll.vcxproj")
        final greetProject = projectFile("exe/exe_greetingsLib.vcxproj")
        final mainSolution = solutionFile("exe/exe_mainExe.sln")

        and:
        mainSolution.assertHasProjects("exe_mainExe", "lib_helloDll", "exe_greetingsLib")
        mainSolution.assertReferencesProject(exeProject, projectConfigurations)
        mainSolution.assertReferencesProject(helloProject, projectConfigurations)
        mainSolution.assertReferencesProject(greetProject, projectConfigurations)

        and:
        exeProject.projectConfigurations['debug'].includePath == filePath("src/main/headers", "../lib/src/hello/headers")
        helloProject.projectConfigurations['debug'].includePath == filePath("src/hello/headers", "../exe/src/greetings/headers")
        greetProject.projectConfigurations['debug'].includePath == filePath("src/greetings/headers")
    }

    def "detects gradle wrapper and uses in vs project"() {
        when:
        def gradlew = file("gradlew.bat") << "dummy wrapper"

        settingsFile.text = "include ':exe'"
        buildFile << """
project(':exe') {
    model {
        components {
            main(NativeExecutableSpec)
        }
    }
}
"""
        and:
        run ":exe:mainVisualStudio"

        then:
        final exeProject = projectFile("exe/exe_mainExe.vcxproj")
        exeProject.projectConfigurations.values().each {
            assert it.buildCommand == "../gradlew.bat -p \"..\" :exe:installMain${it.name.capitalize()}Executable"
        }
    }

    def "cleanVisualStudio removes all generated visual studio files"() {
        when:
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
project(':exe') {
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
                    cpp.lib project: ':lib', library: 'main', linkage: 'static'
                }
            }
        }
    }
}
project(':lib') {
    model {
        components {
            main(NativeLibrarySpec)
        }
    }
}
"""
        and:
        run "mainVisualStudio"

        then:
        def generatedFiles = [
                file("exe/exe_mainExe.sln"),
                file("exe/exe_mainExe.vcxproj"),
                file("exe/exe_mainExe.vcxproj.filters"),
                file("lib/lib_mainDll.sln"),
                file("lib/lib_mainDll.vcxproj"),
                file("lib/lib_mainDll.vcxproj.filters")
        ]
        generatedFiles*.assertExists()

        when:
        run "cleanVisualStudio"

        then:
        generatedFiles*.assertDoesNotExist()
    }

    private SolutionFile solutionFile(String path) {
        return new SolutionFile(file(path))
    }

    private ProjectFile projectFile(String path) {
        return new ProjectFile(file(path))
    }

    private static String filePath(String... paths) {
        return (paths as List).join(";")
    }
}
