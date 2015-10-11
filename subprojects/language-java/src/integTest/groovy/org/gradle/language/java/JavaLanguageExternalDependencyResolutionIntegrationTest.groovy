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

class JavaLanguageExternalDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve dependency on library in maven repository"() {
        given:
        def module = mavenRepo.module("org.gradle", "test").publish()

        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}
repositories {
    maven { url '${mavenRepo.uri}' }
}
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'org.gradle:test:1.0'
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
        file('src/main/java/TestApp.java') << '''public class TestApp {}'''

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)
    }

    def "resolved classpath includes compile-scoped but not runtime-scoped transitive dependencies for library in maven repository"() {
        given:
        def compileDep = mavenRepo.module("org.gradle", "compileDep").publish()
        mavenRepo.module("org.gradle", "runtimeDep").publish()
        def module = mavenRepo.module("org.gradle", "test")
                .dependsOn("org.gradle", "compileDep", "1.0")
                .dependsOn("org.gradle", "runtimeDep", "1.0", null, "runtime")
                .publish()

        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}
repositories {
    maven { url '${mavenRepo.uri}' }
}
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'org.gradle:test:1.0'
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
        file('src/main/java/TestApp.java') << '''public class TestApp {}'''

        when:
        succeeds ':copyDeps'

        then:
        file('libs').assertHasDescendants('test-1.0.jar', 'compileDep-1.0.jar')
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)
        file('libs/compileDep-1.0.jar').assertIsCopyOf(compileDep.artifactFile)
    }

    def "resolved classpath does not include transitive compile-scoped dependencies of local components"() {
        given:
        mavenRepo.module("org.gradle", "compileDep").publish()

        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}
repositories {
    maven { url '${mavenRepo.uri}' }
}
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
                        library 'org.gradle:compileDep:1.0'
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
        file('src/main/java/TestApp.java') << '''public class TestApp {}'''
        file('src/other/java/Other.java') << '''public class Other {}'''

        when:
        succeeds ':copyDeps'

        then:
        file('mainLibs').assertHasDescendants('other.jar')
        file('otherLibs').assertHasDescendants('compileDep-1.0.jar')
    }
}
