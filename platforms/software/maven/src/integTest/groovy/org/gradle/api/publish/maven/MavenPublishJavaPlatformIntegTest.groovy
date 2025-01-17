/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenJavaPlatformModule

class MavenPublishJavaPlatformIntegTest extends AbstractMavenPublishIntegTest {
    MavenJavaPlatformModule javaPlatform = javaPlatform(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    def "can publish java-platform with no dependencies"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaPlatform.assertPublished()
        javaPlatform.assertNoDependencies()

        and:
        resolveArtifacts(javaPlatform) { noFiles() }
        resolveApiArtifacts(javaPlatform) { noFiles() }
        resolveRuntimeArtifacts(javaPlatform) { noFiles() }
    }

    def "can publish java-platform with constraints"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                constraints {
                    api "org.test:foo:1.0"
                    runtime "org.test:bar:1.0"
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaPlatform.assertPublished()
        javaPlatform.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:foo:1.0")
            noMoreDependencies()
        }
        javaPlatform.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:foo:1.0")
            constraint("org.test:bar:1.0")
            noMoreDependencies()
        }
        javaPlatform.parsedPom.scopes.keySet() == ['no_scope'] as Set
        javaPlatform.parsedPom.scope('no_scope') {
            assertNoDependencies()
            assertDependencyManagement("org.test:bar:1.0", "org.test:foo:1.0")
        }
    }

    def "can define a platform with local projects"() {
        given:
        createDirs("core", "utils")
        settingsFile << """
            include "core"
            include "utils"
        """

        createBuildScripts("""
            dependencies {
                constraints {
                    api project(":core")
                    runtime project(":utils")
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaPlatform.assertPublished()
        javaPlatform.parsedModuleMetadata.variant("apiElements") {
            constraint("org.gradle.test:core:1.9")
            noMoreDependencies()
        }
        javaPlatform.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.gradle.test:core:1.9")
            constraint("org.gradle.test:utils:1.9")
            noMoreDependencies()
        }
        javaPlatform.parsedPom.scopes.keySet() == ['no_scope'] as Set
        javaPlatform.parsedPom.scope('no_scope') {
            assertNoDependencies()
            assertDependencyManagement("org.gradle.test:core:1.9", "org.gradle.test:utils:1.9")
        }
    }

    def "can define a platform with local projects with customized artifacts"() {
        given:
        createDirs("core", "utils")
        settingsFile << """
            include "core"
            include "utils"
        """

        createBuildScripts("""
            dependencies {
                constraints {
                    api project(":core")
                    runtime project(":utils")
                }
            }
            subprojects {
                version = null
                apply(plugin: 'java-library')
                apply(plugin: 'maven-publish')
                publishing {
                    publications {
                        maven(MavenPublication) {
                            group = "custom-group"
                            artifactId = project.name + "-custom"
                            version = "1.9"
                            from components.java
                        }
                    }
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaPlatform.assertPublished()
        javaPlatform.parsedModuleMetadata.variant("apiElements") {
            constraint("custom-group:core-custom:1.9")
            noMoreDependencies()
        }
        javaPlatform.parsedModuleMetadata.variant("runtimeElements") {
            constraint("custom-group:core-custom:1.9")
            constraint("custom-group:utils-custom:1.9")
            noMoreDependencies()
        }
        javaPlatform.parsedPom.scopes.keySet() == ['no_scope'] as Set
        javaPlatform.parsedPom.scope('no_scope') {
            assertNoDependencies()
            assertDependencyManagement("custom-group:core-custom:1.9", "custom-group:utils-custom:1.9")
        }
    }

    def "can publish java-platform with resolved versions"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "foo", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            repositories { maven { url = "${mavenRepo.uri}" } }
            dependencies {
                constraints {
                    api "org.test:foo:+"
                }
                api "org.test:foo"
            }

            javaPlatform { allowDependencies() }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                        versionMapping {
                            allVariants {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaPlatform.assertPublished()
        javaPlatform.parsedModuleMetadata.variant("apiElements") {
            constraint("org.test:foo:1.1")
            dependency("org.test:foo:1.1")
            noMoreDependencies()
        }
        javaPlatform.parsedModuleMetadata.variant("runtimeElements") {
            constraint("org.test:foo:1.1")
            dependency("org.test:foo:1.1")
            noMoreDependencies()
        }
        javaPlatform.parsedPom.scopes.keySet() == ['compile', 'no_scope'] as Set
        javaPlatform.parsedPom.scope('compile') {
            assertDependsOn("org.test:foo:1.1")
            assertNoDependencyManagement()
        }
        javaPlatform.parsedPom.scope('no_scope') {
            assertNoDependencies()
            assertDependencyManagement("org.test:foo:1.1")
        }
        javaPlatform.parsedPom.hasNoScope('runtime')
    }


    private String createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-platform'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }

            allprojects {
                group = 'org.gradle.test'
                version = '1.9'
            }

$append
"""

    }
}
