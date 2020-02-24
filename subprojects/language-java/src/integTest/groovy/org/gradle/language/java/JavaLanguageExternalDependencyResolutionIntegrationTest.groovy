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

class JavaLanguageExternalDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def theModel(String model) {
        applyJavaPlugin(buildFile, executer)
        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
        """
        buildFile << model
    }

    def setup() {
        file('src/main/java/TestApp.java') << 'public class TestApp {}'
    }

    @Unroll
    def "can resolve dependency on library in maven repository using #description"() {
        given:
        def module = mavenRepo.module("org.gradle", "test").publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    $declaration
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps', Copy) {
                        into 'libs'
                        from compileMainJarMainJava.classpath
                    }
                }
            }
        """

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)

        where:
        description                                   | declaration
        "shorthand notation"                          | "module 'org.gradle:test:1.0'"
        "shorthand notation without a version number" | "module 'org.gradle:test'"
        "explicit notation without a version number"  | "group 'org.gradle' module 'test'"
        "explicit notation starting from module"      | "module 'test' group 'org.gradle' version '1.0'"
    }

    def "resolved classpath includes compile-scoped but not runtime-scoped transitive dependencies for library in maven repository"() {
        given:
        def compileDep = mavenRepo.module("org.gradle", "compileDep").publish()
        mavenRepo.module("org.gradle", "runtimeDep").publish()
        def module = mavenRepo.module("org.gradle", "test")
                .dependsOn("org.gradle", "compileDep", "1.0")
                .dependsOn("org.gradle", "runtimeDep", "1.0", null, "runtime", null)
                .publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    module 'org.gradle:test:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps', Copy) {
                        into 'libs'
                        from compileMainJarMainJava.classpath
                    }
                }
            }
        """

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar', 'compileDep-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)
        file('libs/compileDep-1.0.jar').assertIsCopyOf(compileDep.artifactFile)
    }

    @ToBeFixedForInstantExecution
    def "resolved classpath does not include transitive compile-scoped maven dependencies of local components"() {
        given:
        mavenRepo.module("org.gradle", "compileDep").publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'other'
                                }
                            }
                        }
                    }
                    other(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    module 'org.gradle:compileDep:1.0'
                                }
                            }
                        }
                    }
                }
                tasks {
                    create('copyDeps') {
                        dependsOn 'copyMainDeps'
                        dependsOn 'copyOtherDeps'
                    }
                    create('copyMainDeps', Copy) {
                        into 'mainLibs'
                        from compileMainJarMainJava.classpath
                    }
                    create('copyOtherDeps', Copy) {
                        into 'otherLibs'
                        from compileOtherJarOtherJava.classpath
                    }
                }
            }
        """
        file('src/other/java/Other.java')  << 'public class Other {}'

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar')
        file('otherLibs').assertHasDescendants('compileDep-1.0.jar')
    }

    @ToBeFixedForInstantExecution
    def "resolved classpath includes transitive api-scoped dependencies of maven library dependency"() {
        given:
        mavenRepo.module("org.gradle", "compileDep").publish()
        mavenRepo.module("org.gradle", "transitiveDep").publish()
        mavenRepo.module("org.gradle", "transitiveApiDep").publish()
        mavenRepo.module("org.gradle", "apiDep")
                .dependsOn("org.gradle", "transitiveApiDep", "1.0", null, "compile", null)
                .dependsOn("org.gradle", "transitiveDep", "1.0", null, "runtime", null)
                .publish()

        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        sources {
                            java {
                                dependencies {
                                    library 'other'
                                }
                            }
                        }
                    }
                    other(JvmLibrarySpec) {
                        api {
                            dependencies {
                                module 'org.gradle:apiDep:1.0'
                            }
                        }
                        sources {
                            java {
                                dependencies {
                                    module 'org.gradle:compileDep:1.0'
                                }
                            }
                        }
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

        file('src/other/java/Other.java') << 'public class Other {}'

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar', 'apiDep-1.0.jar', 'transitiveApiDep-1.0.jar')
    }

    @ToBeFixedForInstantExecution
    def "reasonable error message when external dependency cannot be found"() {
        given:
        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        dependencies.module 'org.gradle:test:1.0'
                    }
                }
            }
        """

        expect:
        fails 'mainJar'

        and:
        failureCauseContains("Could not resolve all dependencies for 'Jar 'main:jar''")
        failureCauseContains('Could not find org.gradle:test:1.0')
    }

    def "reasonable error message when specifying a module id via library DSL"() {
        given:
        theModel """
            model {
                components {
                    main(JvmLibrarySpec) {
                        dependencies.library 'org.gradle:test:1.0'
                    }
                }
            }
        """

        expect:
        fails 'mainJar'

        and:
        failureCauseContains("'org.gradle:test:1.0' is not a valid library name. Did you mean to refer to a module instead?")
    }
}
