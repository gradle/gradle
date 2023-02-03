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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.vcs.git.GitVersionControlSpec

class MappingSourceDependencyMultiprojectIntegrationTest extends AbstractSourceDependencyMultiprojectIntegrationTest {
    @Override
    void mappingFor(String gitRepo, String coords, String repoDef) {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("${coords}") {
                        from(${GitVersionControlSpec.name}) {
                            url = uri("$gitRepo")
                            ${repoDef}
                        }
                    }
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "can map all modules in group to repo using all {}"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.test") {
                            from(GitVersionControlSpec) {
                                url = "${repo.url}"
                            }
                        }
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """

        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")
    }

    @ToBeFixedForConfigurationCache
    def "can map to a repository containing multiple separate builds"() {
        repo.file("settings.gradle").delete()
        repo.file("foo/build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '2.0'
        """
        repo.file("bar/build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '3.0'
        """
        repo.commit('updated')

        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.test") {
                            from(GitVersionControlSpec) {
                                url = "${repo.url}"
                                rootDir = details.requested.module
                            }
                        }
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """

        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-2.0.jar", "bar-3.0.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-2.0.jar", "bar-3.0.jar")
    }
}
