/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class NativeDependentComponentsReportIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "displays dependents report for all components of the task's project"() {
        given:
        buildScript simpleCppBuild()

        when:
        run "dependentComponents"

        then:
        output.contains simpleCppUtilDependents()
        output.contains simpleCppLibDependents()
        output.contains simpleCppMainDependents()
    }

    @Unroll
    def "displays dependents of targeted '#component' component"() {
        given:
        buildScript simpleCppBuild()

        when:
        run 'dependentComponents', '--component', component

        then:
        outputContains dependentsReport

        where:
        component | dependentsReport
        'util'    | simpleCppUtilDependents()
        'lib'     | simpleCppLibDependents()
        'main'    | simpleCppMainDependents()
    }

    def "displays dependent of multiple targeted components"() {
        given:
        buildScript simpleCppBuild()

        when:
        run 'dependentComponents', '--component', 'lib', '--component', 'main'

        then:
        output.contains simpleCppLibDependents()
        output.contains simpleCppMainDependents()
        !output.contains(simpleCppUtilDependents())
    }

    def "displays non-buildable dependents"() {
        given:
        buildScript simpleCppBuild() + '''
            model {
                components {
                    lib {
                        binaries.all {
                            buildable = false
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'dependentComponents'

        then:
        output.contains '''
            lib - Components that depend on native library 'lib'
            +--- lib:sharedLibrary NOT BUILDABLE
            |    \\--- main:executable
            \\--- lib:staticLibrary NOT BUILDABLE

            main - Components that depend on native executable 'main'
            \\--- main:executable

            util - Components that depend on native library 'util'
            +--- util:sharedLibrary
            |    +--- lib:sharedLibrary NOT BUILDABLE
            |    |    \\--- main:executable
            |    +--- lib:staticLibrary NOT BUILDABLE
            |    \\--- main:executable
            \\--- util:staticLibrary
        '''.stripIndent()
    }

    def "displays dependents across projects in a build"() {
        given:
        settingsFile.text = multiProjectSettings()
        buildScript multiProjectBuild()

        when:
        run 'libraries:dependentComponents', '--component', 'foo'

        then:
        output.contains '''
            ------------------------------------------------------------
            Project :libraries
            ------------------------------------------------------------

            foo - Components that depend on native library 'foo'
            +--- :libraries:foo:sharedLibrary
            |    +--- :bootstrap:main:executable
            |    +--- :extensions:bazar:sharedLibrary
            |    |    \\--- :bootstrap:main:executable
            |    \\--- :extensions:bazar:staticLibrary
            \\--- :libraries:foo:staticLibrary
        '''.stripIndent()
    }

    def "don't fail with prebuilt libraries"() {
        given:
        buildScript simpleBuildWithPrebuiltLibrary()

        expect:
        succeeds 'dependentComponents'
    }

    def "displays dependent test suites"() {
        given:
        buildScript simpleBuildWithTestSuites()

        when:
        run 'dependentComponents'

        then:
        output.contains """
            lib - Components that depend on native library 'lib'
            +--- lib:sharedLibrary
            |    \\--- main:executable
            \\--- lib:staticLibrary
                 \\--- libTest:googleTestExe (t)

            main - Components that depend on native executable 'main'
            \\--- main:executable

            util - Components that depend on native library 'util'
            +--- util:sharedLibrary
            |    +--- lib:sharedLibrary
            |    |    \\--- main:executable
            |    +--- lib:staticLibrary
            |    |    \\--- libTest:googleTestExe (t)
            |    +--- main:executable
            |    \\--- libTest:googleTestExe (t)
            \\--- util:staticLibrary
                 \\--- utilTest:cUnitExe (t)

            libTest - Components that depend on Google test suite 'libTest'
            \\--- libTest:googleTestExe (t)

            utilTest - Components that depend on Cunit test suite 'utilTest'
            \\--- utilTest:cUnitExe (t)

            (t) - Test suite binary
        """.stripIndent()
    }

    // TODO: This may or may not make sense (we shouldn't fail any worse than building would)
    @NotYetImplemented
    def "circular dependencies are handled gracefully"() {
        buildFile << """
apply plugin: 'cpp'
model {
    components {
        util(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }

        lib(NativeLibrarySpec) {
            sources {
                cpp.lib library: 'util'
            }
        }

        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }
    }
}
"""
        when:
        succeeds("dependentComponents")
        then:
        false // TODO: once this works, assert the correct output.
        // result.output.contains("Foo")
    }

    def "report renders variant binaries"() {
        buildFile << """
apply plugin: 'cpp'
model {
    flavors {
        freeware
        shareware
        shrinkware
    }

    components {
        lib(NativeLibrarySpec)

        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'lib'
            }
        }
    }
}
"""
        when:
        succeeds("dependentComponents")
        then:
        result.output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

lib - Components that depend on native library 'lib'
+--- lib:freewareSharedLibrary
|    \\--- main:freewareExecutable
+--- lib:freewareStaticLibrary
+--- lib:sharewareSharedLibrary
|    \\--- main:sharewareExecutable
+--- lib:sharewareStaticLibrary
+--- lib:shrinkwareSharedLibrary
|    \\--- main:shrinkwareExecutable
\\--- lib:shrinkwareStaticLibrary

main - Components that depend on native executable 'main'
+--- main:freewareExecutable
+--- main:sharewareExecutable
\\--- main:shrinkwareExecutable
""")
    }

    private static String simpleCppBuild() {
        return """
            plugins {
                id 'c'
                id 'cpp'
            }
            model {
                components {
                    util(NativeLibrarySpec)
                    lib(NativeLibrarySpec) {
                        sources {
                            cpp {
                                lib library: 'util'
                            }
                        }
                    }
                    main(NativeExecutableSpec) {
                        sources {
                            cpp {
                                lib library: 'util'
                                lib library: 'lib'
                            }
                        }
                    }
                }
            }
        """.stripIndent()
    }

    private static String simpleBuildWithPrebuiltLibrary() {
        return simpleCppBuild() + """
            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        prebuiltlib {
                        }
                    }
                }
                components {
                    util {
                        sources.c {
                            lib library: 'prebuiltlib', linkage: 'static'
                        }
                    }
                }
            }
        """.stripIndent()
    }

    private static String simpleBuildWithTestSuites() {
        return simpleCppBuild() + """
            apply plugin: 'cunit-test-suite'
            apply plugin: 'google-test-test-suite'
            model {
                testSuites {
                    utilTest(CUnitTestSuiteSpec) {
                        testing \$.components.util
                    }
                    libTest(GoogleTestTestSuiteSpec) {
                        testing \$.components.lib
                    }
                }
            }
        """.stripIndent()
    }

    private static String simpleCppLibDependents() {
        return '''
            lib - Components that depend on native library 'lib'
            +--- lib:sharedLibrary
            |    \\--- main:executable
            \\--- lib:staticLibrary
        '''.stripIndent()
    }

    private static String simpleCppMainDependents() {
        return '''
            main - Components that depend on native executable 'main'
            \\--- main:executable
        '''.stripIndent()
    }

    private static String simpleCppUtilDependents() {
        return '''
            util - Components that depend on native library 'util'
            +--- util:sharedLibrary
            |    +--- lib:sharedLibrary
            |    |    \\--- main:executable
            |    +--- lib:staticLibrary
            |    \\--- main:executable
            \\--- util:staticLibrary
        '''.stripIndent()
    }

    private static String multiProjectSettings() {
        return '''
            include 'api'
            include 'spi'
            include 'runtime'
            include 'extensions'
            include 'libraries'
            include 'bootstrap'

            rootProject.name = 'dependentsMulti'
        '''.stripIndent()
    }

    private static String multiProjectBuild() {
        return '''
            subprojects {
                apply plugin: 'cpp'
            }
            project(':api') {
                model {
                    components {
                        api(NativeLibrarySpec)
                    }
                }
            }
            project(':spi') {
                model {
                    components {
                        spi(NativeLibrarySpec) {
                            sources.cpp {
                                    lib project: ':api', library: 'api'
                            }
                        }
                    }
                }
            }
            project(':runtime') {
                model {
                    components {
                        runtime(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                    lib project: ':spi', library: 'spi'
                                }
                            }
                        }
                    }
                }
            }
            project(':libraries') {
                model {
                    components {
                        foo(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                }
                            }
                        }
                        bar(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                }
                            }
                        }
                    }
                }
            }
            project(':extensions') {
                model {
                    components {
                        bazar(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                    lib project: ':spi', library: 'spi'
                                    lib project: ':libraries', library: 'foo'
                                }
                            }
                        }
                        cathedral(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                    lib project: ':spi', library: 'spi'
                                    lib project: ':libraries', library: 'bar'
                                }
                            }
                        }
                    }
                }
            }
            project(':bootstrap') {
                model {
                    components {
                        bootstrap(NativeLibrarySpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                    lib project: ':spi', library: 'spi'
                                    lib project: ':runtime', library: 'runtime'
                                }
                            }
                        }
                        main(NativeExecutableSpec) {
                            sources {
                                cpp {
                                    lib project: ':api', library: 'api'
                                    lib project: ':spi', library: 'spi'
                                    lib project: ':runtime', library: 'runtime'
                                    lib library: 'bootstrap'
                                    lib project: ':libraries', library: 'foo'
                                    lib project: ':libraries', library: 'bar'
                                    lib project: ':extensions', library: 'bazar'
                                    lib project: ':extensions', library: 'cathedral'
                                }
                            }
                        }
                    }
                }
            }
        '''.stripIndent()
    }
}
