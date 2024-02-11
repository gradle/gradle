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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.util.internal.TextUtil

class PublishAndResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'ivy-publish'

                group = 'org.gradle.test'
                version = '1.9'

                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "can resolve static dependency published by a dependent task in the same project"() {
        given:
        buildFile << """
            ${taskWhichPublishes('api', '1.1')}
            ${taskWhichResolves('api', '1.1')}
        """

        expect:
        succeeds resolveTask
        versionIsCopiedAndExists("api", "1.1")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve static dependency published by a dependent task in another project in the same build"() {
        createDirs("child")
        settingsFile << """
            include ':child'
        """

        given:
        buildFile << """
            ${taskWhichPublishes('api', '1.1')}
            project(':child') {
                ${taskWhichResolves('api', '1.1')}
            }
        """

        expect:
        succeeds ":child:${resolveTask}"
        versionIsCopiedAndExists("api", "1.1", "child/")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve dynamic dependency published by a dependent task"() {
        ivyRepo.module('org.gradle.test', 'api', '1.0').publish()

        given:
        buildFile << """
            ${taskWhichPublishes('api', '1.1')}
            ${taskWhichResolves('api', '1.+')}
        """

        expect:
        succeeds resolveTask
        versionIsCopiedAndExists("api", "1.1")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve dependency published by a custom publishing task"() {
        def tmpRepo = new IvyFileRepository(file("tmp-repo"))
        tmpRepo.module('org.gradle.test', 'api', '1.1').publish()

        given:
        buildFile << """
            task customPublish(type: Copy) {
                from "${safePath(tmpRepo.moduleDir('org.gradle.test', 'api'), '1.1')}"
                into "${safePath(ivyRepo.moduleDir('org.gradle.test', 'api'), '1.1')}"
            }
            ${taskWhichResolves('api', '1.1')}
            ${resolveTask}.dependsOn customPublish
        """

        expect:
        succeeds resolveTask
        versionIsCopiedAndExists("api", "1.1")
    }

    def safePath(Object... paths) {
        return TextUtil.escapeString(paths.join(File.separator))
    }

    def versionIsCopiedAndExists(lib, version, root="") {
        assert TextUtil.normaliseFileSeparators(output).contains("ivy-repo/org.gradle.test/${lib}/${version}/${lib}-${version}.jar")
        testDirectory.assertContainsDescendants("${root}build/copies/${lib}-${version}.jar")
        true
    }

    def resolveTask = "resolveAndCopy"

    def taskWhichPublishes(lib, version) {
        return """
            publishing {
                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
                publications {
                    jar(IvyPublication) {
                        from components.java
                        organisation group
                        module '${lib}'
                        revision '${version}'
                    }
                }
            }
        """
    }

    def taskWhichResolves(lib, resolveVersion) {
        return """
            configurations {
                testartifacts
            }

            dependencies {
                testartifacts 'org.gradle.test:${lib}:${resolveVersion}'
            }

            task ${resolveTask}(type: Copy) {
                dependsOn ":publish"
                from {
                    configurations.testartifacts
                }
                into "\${buildDir}/copies"
                eachFile { println it.file.path - rootDir.path }
            }
        """
    }
}
