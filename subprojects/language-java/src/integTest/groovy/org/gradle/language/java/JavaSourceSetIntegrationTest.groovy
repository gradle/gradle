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
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.JarTestFixture

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin

@TestReproducibleArchives
class JavaSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "can define dependencies on Java source set"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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
        applyJavaPlugin(buildFile)
        buildFile << '''
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
                assert deps[0] instanceof org.gradle.platform.base.internal.DefaultProjectDependencySpec // this guy is immutable
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

    def "reports failure for invalid dependency notation"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        $notation
                    }
                }
            }
        }
    }

    tasks {
        create('printDependencies') {
            doLast {
                println \$('components.main.sources.java').dependencies.dependencies*.displayName
            }
        }
    }
}
"""
        when:
        fails "printDependencies"

        then:
        failure.assertHasCause(failureMessage)

        where:
        notation                         | failureMessage
        "library(null)"                  | 'A project dependency must have at least a project or library name specified.'
        "group 'group-without-a-module'" | 'A module dependency must have at least a group and a module name specified.'
    }

    def "can build JAR from multiple source sets"() {
        given:
        file("src/main/java/Main.java") << "public class Main {}"
        file("src/main/resources/main.properties") << "java=7"
        file("src/main/java8/Java8.java") << "public class Java8 {}"
        file("src/main/java8-resources/java8.properties") << "java=8"

        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java7'
            targetPlatform 'java8'
            binaries {
                withType(JarBinarySpec) { binary ->
                    if (binary.targetPlatform.name == "java8") {
                        sources {
                            java8(JavaSourceSet) {
                                source.srcDir "src/main/java8"
                            }
                            java8Resources(JvmResourceSet) {
                                source.srcDir "src/main/java8-resources"
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
        new JarTestFixture(file("build/jars/main/java7Jar/main.jar")).hasDescendants(
            "Main.class", "main.properties");
        new JarTestFixture(file("build/jars/main/java8Jar/main.jar")).hasDescendants(
            "Main.class", "main.properties", "Java8.class", "java8.properties");
    }

}
