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

package org.gradle.api.reporting.dependents

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class DependentComponentsReportIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "help displays dependents report task options"() {
        when:
        run "help", "--task", "dependentComponents"

        then:
        output.contains("Displays the dependent components of root project 'test'. [incubating]")
        output.contains("--component     The component to generate the report for.")
    }

    def "displays empty dependents report for an empty project"() {
        given:
        buildFile

        when:
        run "dependentComponents"

        then:
        output.contains("No components.");
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
            lib - Dependent components for native library 'lib'
            +--- lib:sharedLibrary NOT BUILDABLE
            |    \\--- main:executable
            \\--- lib:staticLibrary NOT BUILDABLE

            main - Dependent components for native executable 'main'
            \\--- main:executable

            util - Dependent components for native library 'util'
            +--- util:sharedLibrary
            |    +--- lib:sharedLibrary NOT BUILDABLE
            |    |    \\--- main:executable
            |    \\--- lib:staticLibrary NOT BUILDABLE
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

            foo - Dependent components for native library 'foo'
            +--- :libraries:foo:sharedLibrary
            |    +--- :bootstrap:main:executable
            |    +--- :extensions:bazar:sharedLibrary
            |    |    \\--- :bootstrap:main:executable
            |    \\--- :extensions:bazar:staticLibrary
            \\--- :libraries:foo:staticLibrary
        '''.stripIndent()
    }

    private static String simpleCppBuild() {
        return """
            plugins {
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
                                lib library: 'lib'
                            }
                        }
                    }
                }
            }
        """.stripIndent()
    }

    private static String simpleCppLibDependents() {
        return '''
            lib - Dependent components for native library 'lib'
            +--- lib:sharedLibrary
            |    \\--- main:executable
            \\--- lib:staticLibrary
        '''.stripIndent()
    }

    private static String simpleCppMainDependents() {
        return '''
            main - Dependent components for native executable 'main'
            \\--- main:executable
        '''.stripIndent()
    }

    private static String simpleCppUtilDependents() {
        return '''
            util - Dependent components for native library 'util'
            +--- util:sharedLibrary
            |    +--- lib:sharedLibrary
            |    |    \\--- main:executable
            |    \\--- lib:staticLibrary
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
