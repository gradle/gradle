/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

@Ignore("skip these tests until we add some kind of implementation that uses vcs mappings")
class VcsMappingsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            import ${DirectoryRepository.canonicalName}
        """
        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'
            
            dependencies {
                compile "org.test:dep:1.0"
            }
        """
        file("src/main/java/Main.java") << """
            public class Main {
                Dep dep = null;
            }
        """
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("dep") {
            buildFile << """
                apply plugin: 'java'
            """
            file("src/main/java/Dep.java") << "public class Dep {}"
        }
    }

    def "can define and use source repositories"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepository) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        file("dep/checkedout").assertIsFile()
    }

    def "can define and use source repositories with all {}"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule("rule") { details ->
                        if (details.requested.group == "org.test") {
                            from vcs(DirectoryRepository) {
                                sourceDir = file("dep")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        file("dep/checkedout").assertIsFile()
    }

    def "can define unused vcs mappings"() {
        settingsFile << """
            // include the missing dep as a composite
            includeBuild 'dep'
            
            sourceControl {
                vcsMappings {
                    withModule("unused:dep") {
                        from vcs(DirectoryRepository) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    addRule("rule") { details ->
                        if (details.requested.group == "unused") {
                            from vcs(DirectoryRepository) {
                                sourceDir = file("does-not-exist")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        file("dep/checkedout").assertDoesNotExist()
        file("does-not-exist/checkedout").assertDoesNotExist()
    }

    def "last vcs mapping rule wins"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepository) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepository) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        file("dep/checkedout").assertIsFile()
        file("does-not-exist/checkedout").assertDoesNotExist()
    }
}
