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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ResolvedFileOrderingIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    String getHeader() {
        """
            dependencies {
                attributesSchema {
                   attribute(Attribute.of('usage', String))
                }
            }
            configurations {
                compile
                "default" {
                    extendsFrom compile
                }
            }
        """
    }

    def "result includes files from local and external components and file dependencies in a fixed order and with duplicates removed"() {
        mavenRepo.module("org", "test", "1.0")
            .artifact(classifier: 'from-main')
            .artifact(classifier: 'from-a')
            .artifact(classifier: 'from-c')
            .publish()
        mavenRepo.module("org", "test2", "1.0").publish()
        mavenRepo.module("org", "test3", "1.0").publish()

        settingsFile << """
            include 'a'
            include 'b'
            include 'c'
            dependencyResolutionManagement {
                repositories { maven { url = '$mavenRepo.uri' } }
            }
        """
        buildFile << """
            $header
            dependencies {
                compile files('test-lib.jar')
                compile project(':a')
                compile 'org:test:1.0'
                compile('org:test:1.0') {
                    artifact {
                        name = 'test'
                        classifier = 'from-main'
                        type = 'jar'
                    }
                }
                artifacts {
                    compile file('test.jar')
                }
            }

            task show {
                def compile = providers.provider { configurations.compile }

                def artifacts1 = compile.map { it.incoming.artifacts.collect { it.file.name } }
                def artifacts2 = compile.map { it.incoming.artifactView { }.artifacts.collect { it.file.name } }
                def artifacts3 = compile.map { it.incoming.artifactView { lenient = true }.artifacts.collect { it.file.name } }

                def files1 = compile.map { it.collect { it.name } }
                def files2 = compile.map { it.incoming.files.collect { it.name } }
                def files3 = compile.map { it.incoming.artifacts.artifactFiles.collect { it.name } }
                def files4 = compile.map { it.incoming.artifactView { }.files.collect { it.name } }
                def files5 = compile.map { it.incoming.artifactView { }.artifacts.artifactFiles.collect { it.name } }
                def files6 = compile.map { it.incoming.artifactView { lenient = true }.files.collect { it.name } }
                def files7 = compile.map { it.incoming.artifactView { lenient = true }.artifacts.artifactFiles.collect { it.name } }
                def files8 = compile.map { it.incoming.artifactView { componentFilter { true } }.files.collect { it.name } }
                def files9 = compile.map { it.incoming.artifactView { componentFilter { true } }.artifacts.artifactFiles.collect { it.name } }
                
                doLast {
                    println "artifacts 1: " + artifacts1.get()
                    println "artifacts 2: " + artifacts2.get()
                    println "artifacts 3: " + artifacts3.get()

                    println "files 1: " + files1.get()
                    println "files 2: " + files2.get()
                    println "files 3: " + files3.get()
                    println "files 4: " + files4.get()
                    println "files 5: " + files5.get()
                    println "files 6: " + files6.get()
                    println "files 7: " + files7.get()
                    println "files 8: " + files8.get()
                    println "files 9: " + files9.get()
                }
            }
        """

        file("a/build.gradle") << """
            $header

            dependencies {
                compile files('a-lib.jar')
                compile project(':b')
                compile project(':c')
                compile 'org:test:1.0'
                compile('org:test:1.0') {
                    artifact {
                        name = 'test'
                        classifier = 'from-a'
                        type = 'jar'
                    }
                }
                compile('org:test:1.0') {
                    artifact {
                        name = 'test'
                        classifier = 'from-a'
                        type = 'jar'
                    }
                }
            }
            artifacts {
                compile file('a.jar')
                compile file('a.jar')
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile files('b-lib.jar')
                compile files('b-lib.jar')
                compile 'org:test2:1.0'
                compile project(':c')
            }
            artifacts {
                compile file('b.jar')
            }
        """

        file("c/build.gradle") << """
            $header

            dependencies {
                compile files('c-lib.jar')
                compile 'org:test3:1.0'
                compile('org:test:1.0') {
                    artifact {
                        name = 'test'
                        classifier = 'from-c'
                        type = 'jar'
                    }
                    artifact {
                        // this is the default artifact
                        name = 'test'
                        type = 'jar'
                    }
                }
            }
            artifacts {
                compile file('c.jar')
            }
        """

        when:
        succeeds 'show'

        then:
        // local artifacts are not de-duplicated. This is documenting existing behaviour rather than desired behaviour
        outputContains("artifacts 1: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("artifacts 2: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("artifacts 3: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")

        outputContains("files 1: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 2: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 3: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 4: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 5: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 6: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 7: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 8: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 9: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
    }
}
