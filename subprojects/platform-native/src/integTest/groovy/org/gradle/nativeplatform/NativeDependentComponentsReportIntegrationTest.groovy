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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

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
        outputContains simpleCppUtilDependents()
        outputContains simpleCppLibDependents()
        outputContains simpleCppMainDependents()
    }

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

    def "fails when targeted component is not found"() {
        given:
        buildScript simpleCppBuild()

        when:
        fails 'dependentComponents', '--component', 'unknown'

        then:
        failure.assertHasCause "Component 'unknown' not found."
    }

    def "fails when some of the targeted components are not found"() {
        given:
        buildScript simpleBuildWithTestSuites()

        when:
        fails 'dependentComponents', '--test-suites', '--component', 'unknown', '--component', 'anonymous', '--component', 'whatever', '--component', 'lib', '--component', 'main', '--component', 'libTest'

        then:
        failure.assertHasCause "Components 'unknown', 'anonymous' and 'whatever' not found."
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

    def "hide non-buildable dependents by default #nonBuildables"() {
        given:
        buildScript simpleCppBuild()
        nonBuildables.each { nonBuildable ->
            buildFile << """
                model {
                    components {
                        $nonBuildable {
                            binaries.all {
                                buildable = false
                            }
                        }
                    }
                }
            """.stripIndent()
        }

        when:
        run 'dependentComponents'

        then:
        output.contains('Some non-buildable components were not shown, use --non-buildable or --all to show them.')
        nonBuildables.each {
            assert !output.contains("$it:")
        }

        where:
        nonBuildables           | _
        ['util']                | _
        ['lib']                 | _
        ['main']                | _
        ['util', 'lib']         | _
        ['util', 'main']        | _
        ['lib', 'main']         | _
        ['util', 'lib', 'main'] | _
    }

    def "displays non-buildable dependents when using #option"() {
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
        run 'dependentComponents', option

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

        where:
        option            | _
        '--all'           | _
        '--non-buildable' | _
    }

    def "consider components with no buildable binaries as non-buildables"() {
        given:
        buildScript simpleCppBuild()
        buildFile << '''
            model {
                components {
                    main {
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
        !output.contains('main')
        output.contains('Some non-buildable components were not shown, use --non-buildable or --all to show them.')
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
            Project ':libraries'
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

    @IgnoreIf({ GradleContextualExecuter.isParallel() })
    def "can show dependent components in parallel"() {
        given: 'a multiproject build'
        settingsFile.text = multiProjectSettings()
        buildScript multiProjectBuild()

        when: 'two reports in parallel'
        succeeds('-q', '--parallel', '--max-workers=4', 'libraries:dependentComponents', 'extensions:dependentComponents')

        then: 'reports are not mixed'
        output.contains '''
            ------------------------------------------------------------
            Project ':libraries'
            ------------------------------------------------------------

            bar - Components that depend on native library 'bar'
            +--- :libraries:bar:sharedLibrary
            |    +--- :bootstrap:main:executable
            |    +--- :extensions:cathedral:sharedLibrary
            |    |    \\--- :bootstrap:main:executable
            |    \\--- :extensions:cathedral:staticLibrary
            \\--- :libraries:bar:staticLibrary

            foo - Components that depend on native library 'foo'
            +--- :libraries:foo:sharedLibrary
            |    +--- :bootstrap:main:executable
            |    +--- :extensions:bazar:sharedLibrary
            |    |    \\--- :bootstrap:main:executable
            |    \\--- :extensions:bazar:staticLibrary
            \\--- :libraries:foo:staticLibrary
            '''.stripIndent()
        output.contains '''
            ------------------------------------------------------------
            Project ':extensions'
            ------------------------------------------------------------

            bazar - Components that depend on native library 'bazar'
            +--- :extensions:bazar:sharedLibrary
            |    \\--- :bootstrap:main:executable
            \\--- :extensions:bazar:staticLibrary

            cathedral - Components that depend on native library 'cathedral'
            +--- :extensions:cathedral:sharedLibrary
            |    \\--- :bootstrap:main:executable
            \\--- :extensions:cathedral:staticLibrary
            '''.stripIndent()
    }

    def "don't fail with prebuilt libraries"() {
        given:
        buildScript simpleBuildWithPrebuiltLibrary()

        expect:
        succeeds 'dependentComponents'
    }

    def "hide test suites by default"() {
        given:
        buildScript simpleBuildWithTestSuites()

        when:
        run 'dependentComponents'

        then:
        !output.contains('utilTest')
        !output.contains('libTest')
        !output.contains('(t) - Test suite binary')
        output.contains 'Some test suites were not shown, use --test-suites or --all to show them.'
    }

    def "displays dependent test suites when using #option"() {
        given:
        buildScript simpleBuildWithTestSuites()

        when:
        run 'dependentComponents', option

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

        where:
        option          | _
        '--all'         | _
        '--test-suites' | _
    }

    def "direct circular dependencies are handled gracefully"() {
        buildScript simpleCppBuild()
        buildFile << '''
            model {
                components {
                    util {
                        sources {
                            cpp.lib library: 'lib'
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        fails 'dependentComponents'

        then:
        failure.assertHasDescription "Execution failed for task ':dependentComponents'."
        failure.assertHasCause '''
            Circular dependency between the following binaries:
            lib:sharedLibrary
            \\--- util:sharedLibrary
                 \\--- lib:sharedLibrary (*)

            (*) - details omitted (listed previously)
            '''.stripIndent().trim()
    }

    def "indirect circular dependencies are handled gracefully"() {
        buildScript simpleCppBuild()
        buildFile << '''
            model {
                components {
                    util {
                        sources {
                            cpp.lib library: 'another'
                        }
                    }
                    another(NativeLibrarySpec) {
                        sources {
                            cpp.lib library: 'lib'
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        fails 'dependentComponents'

        then:
        failure.assertHasDescription "Execution failed for task ':dependentComponents'."
        failure.assertHasCause '''
            Circular dependency between the following binaries:
            another:sharedLibrary
            \\--- util:sharedLibrary
                 \\--- lib:sharedLibrary
                      \\--- another:sharedLibrary (*)

            (*) - details omitted (listed previously)
            '''.stripIndent().trim()
    }

    def "circular dependencies across projects are handled gracefully"() {
        given:
        settingsFile.text = multiProjectSettings()
        buildScript multiProjectBuild()
        buildFile << '''
            project(':api') {
                model {
                    components {
                        api {
                            sources {
                                cpp.lib project: ':bootstrap', library: 'bootstrap'
                            }
                        }
                    }
                }
            }
        '''.stripIndent()

        when:
        fails 'api:dependentComponents'

        then:
        failure.assertHasDescription "Execution failed for task ':api:dependentComponents'."
        failure.assertHasCause '''
            Circular dependency between the following binaries:
            :api:api:sharedLibrary
            \\--- :bootstrap:bootstrap:sharedLibrary
                 \\--- :api:api:sharedLibrary (*)

            (*) - details omitted (listed previously)
            '''.stripIndent().trim()

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
        """.stripIndent()

        when:
        succeeds("dependentComponents")

        then:
        outputContains """
            ------------------------------------------------------------
            Root project 'test'
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
            """.stripIndent()
    }

    @ToBeFixedForConfigurationCache(because = ":dependentComponents")
    def "report for empty build displays no component"() {
        given:
        buildScript emptyNativeBuild()

        when:
        run 'dependentComponents'

        then:
        output.contains emptyDependents()
    }

    @ToBeFixedForConfigurationCache(because = ":dependentComponents")
    def "report for empty build displays no component with task option #option"() {
        given:
        buildScript emptyNativeBuild()

        when:
        run 'dependentComponents', option

        then:
        output.contains emptyDependents()

        where:
        option            | _
        "--test-suites"   | _
        "--non-buildable" | _
        "--all"           | _
    }

    private static String emptyNativeBuild() {
        return """
            plugins {
                id 'c'
                id 'cpp'
            }
        """.stripIndent()
    }

    private static String emptyDependents() {
        return """
            ------------------------------------------------------------
            Root project 'test'
            ------------------------------------------------------------

            No components.

            BUILD SUCCESSFUL
            """.stripIndent().trim()
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
