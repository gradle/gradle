/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin
import static org.gradle.language.java.JavaIntegrationTesting.expectJavaLangPluginDeprecationWarnings

class JavaCompilationAgainstDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    @ToBeFixedForInstantExecution
    def "#scope dependencies are visible from all source sets"() {
        given:
        applyJavaPlugin(buildFile, executer)
        buildFile << """
            model {
                components {
                    main(JvmLibrarySpec) {
                        ${scope.declarationFor 'core'}
                        sources {
                            other(JavaSourceSet) {
                                source.srcDir "src/other"
                            }
                        }
                    }
                    core(JvmLibrarySpec) {}
                }
            }
        """

        file('src/main/java/main/Main.java') << 'package main; class Main extends core.Core {}'
        file('src/other/main/Other.java')    << 'package main; class Other extends core.Core {}'
        file('src/core/java/core/Core.java') << 'package core; public class Core {}'

        expect:
        succeeds 'mainJar'

        where:
        scope << [DependencyScope.API, DependencyScope.COMPONENT]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "resolved classpath for jvm library includes transitive api-scoped dependencies and not #scope dependencies"() {
        given:
        applyJavaPlugin(buildFile, executer)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            ${scope.declarationFor 'other'}
        }
        other(JvmLibrarySpec) {
            api {
                dependencies {
                    library 'apiLib'
                }
            }
            ${scope.declarationFor 'compileLib'}
        }
        apiLib(JvmLibrarySpec) {
            api {
                dependencies {
                    library 'transitiveApiLib'
                }
            }
            ${scope.declarationFor 'transitiveCompileLib'}
        }
        compileLib(JvmLibrarySpec) {
        }
        transitiveApiLib(JvmLibrarySpec) {
        }
        transitiveCompileLib(JvmLibrarySpec) {
        }
    }
    tasks {
        create('copyDeps', Copy) {
            into 'mainLibs'
            from compileMainJarMainJava.classpath
        }
    }
}
"""
        file('src/apiLib/java/ApiLib.java') << '''public class ApiLib {}'''
        file('src/other/java/Other.java') << '''public class Other {}'''
        file('src/transitiveApiLib/java/TransitiveApiLib.java') << '''public class TransitiveApiLib {}'''
        file('src/main/java/TestApp.java') << '''public class TestApp {}'''

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar', 'apiLib.jar', 'transitiveApiLib.jar')

        where:
        scope << [DependencyScope.SOURCES, DependencyScope.COMPONENT]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "when a library dependency is declared at both #scope1 and #scope2 levels, its API is #exportedOrNot"() {
        given:
        applyJavaPlugin(buildFile, executer)
        buildFile << """
            model {
                components {
                    main(JvmLibrarySpec) {
                        dependencies {
                            library 'core'
                        }
                    }
                    core(JvmLibrarySpec) {
                        ${scope1.declarationFor 'lib'}
                        ${scope2.declarationFor 'lib'}
                    }
                    lib(JvmLibrarySpec) {}
                }
            }
        """

        file('src/main/java/main/Main.java') << 'package main; class Main extends lib.Lib {}'
        file('src/core/java/core/Core.java') << 'package core; class Core extends lib.Lib {}'
        file('src/lib/java/lib/Lib.java')    << 'package lib; public class Lib {}'

        expect:
        succeeds 'coreJar'

        and:
        expectJavaLangPluginDeprecationWarnings(executer)
        if (exportedOrNot == 'exported') {
            succeeds 'mainJar'
        } else {
            fails 'mainJar'
        }

        where:
        scope1                    | scope2                  | exportedOrNot
        DependencyScope.COMPONENT | DependencyScope.API     | 'exported'
        DependencyScope.SOURCES   | DependencyScope.API     | 'exported'
        DependencyScope.COMPONENT | DependencyScope.SOURCES | 'not exported'
    }
}
