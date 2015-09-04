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
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class JavaSourceSetIntegrationTest extends AbstractIntegrationSpec {

    void setup() {
        EnableModelDsl.enable(super.executer)
    }

    def "can define dependencies on Java source set"() {
        given:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'someLib' // Library in same project
                        project 'otherProject' library 'someLib' // Library in other project
                        project 'otherProject' // Library in other project, expect exactly one library
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def deps = $('components.main.sources.java').dependencies.dependencies
                assert deps.size() == 3
                assert deps[0].libraryName == 'someLib'
                assert deps[1].projectPath == 'otherProject'
                assert deps[1].libraryName == 'someLib'
                assert deps[2].projectPath == 'otherProject'
                assert deps[2].libraryName == null
            }
        }
    }
}
'''
        when:
        succeeds "checkDependencies"

        then:
        noExceptionThrown()
    }

    def "dependencies returned by the container are immutable"() {
        given:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'someLib'
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def deps = $('components.main.sources.java').dependencies.dependencies
                assert deps.size() == 1
                assert deps[0].libraryName == 'someLib'
                assert deps[0] instanceof org.gradle.platform.base.internal.DefaultDependencySpec // this guy is immutable
                try {
                    deps[0].project('foo')
                    assert false
                } catch (e) {
                    // project('foo') is only available when building the dependencies
                }
            }
        }
    }
}
'''
        when:
        succeeds "checkDependencies"

        then:
        noExceptionThrown()
    }

    def "cannot create a dependency with all null values with library"() {
        given:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library(null)
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def libraries = $('components.main.sources.java').dependencies.dependencies*.libraryName
            }
        }
    }
}
'''
        when:
        fails "checkDependencies"

        then:
        failure.assertHasCause('A dependency spec must have at least one of project or library name not null')
    }

    def "cannot create a dependency with all null values with project"() {
        given:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project(null)
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def libraries = $('components.main.sources.java').dependencies.dependencies*.libraryName
            }
        }
    }
}
'''
        when:
        fails "checkDependencies"

        then:
        failure.assertHasCause('A dependency spec must have at least one of project or library name not null')
    }

    def "filters duplicate dependencies"() {
        given:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'someLib' // Library in same project
                        project 'otherProject' library 'someLib' // Library in other project
                        project 'otherProject' // Library in other project, expect exactly one library

                        // explicitly create duplicates
                        library 'someLib' // Library in same project
                        project 'otherProject' library 'someLib' // Library in other project
                        project 'otherProject' // Library in other project, expect exactly one library
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def deps = $('components.main.sources.java').dependencies.dependencies
                assert deps.size() == 3
                assert deps[0].libraryName == 'someLib'
                assert deps[1].projectPath == 'otherProject'
                assert deps[1].libraryName == 'someLib'
                assert deps[2].projectPath == 'otherProject'
                assert deps[2].libraryName == null
            }
        }
    }
}
'''
        when:
        succeeds "checkDependencies"

        then:
        noExceptionThrown()
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can build JAR from multiple source sets"() {
        given:
        file("src/main/java/Main.java") << "public class Main {}"
        file("src/main/resources/main.properties") << "java=6"
        file("src/main/java7/Java7.java") << "public class Java7 {}"
        file("src/main/java7-resources/java7.properties") << "java=7"

        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            binaries {
                withType(JarBinarySpec) { binary ->
                    if (binary.targetPlatform.name == "java7") {
                        sources {
                            java7(JavaSourceSet) {
                                source.srcDir "src/main/java7"
                            }
                            java7Resources(JvmResourceSet) {
                                source.srcDir "src/main/java7-resources"
                            }
                        }
                    }
                }
            }
        }
    }
}
'''

        when:
        succeeds "assemble"

        then:
        new JarTestFixture(file("build/jars/java6MainJar/main.jar")).hasDescendants(
            "Main.class", "main.properties");
        new JarTestFixture(file("build/jars/java7MainJar/main.jar")).hasDescendants(
            "Main.class", "main.properties", "Java7.class", "java7.properties");
    }

}
