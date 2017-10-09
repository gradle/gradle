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

import org.gradle.util.GFileUtils
import org.gradle.vcs.fixtures.GitRepository
import org.junit.Rule
import spock.lang.Issue

class GitVcsIntegrationTest extends AbstractVcsIntegrationTest {
    @Rule
    GitRepository repo = new GitRepository('dep', temporaryFolder.getTestDirectory())

    def 'can define and use source repositories'() {
        given:
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        // Git repo is cloned
        def gitCheckout = checkoutDir('dep', commit.getId().getName(), "git-repo:${repo.url.toASCIIString()}")
        gitCheckout.file('.git').assertExists()
    }

    @Issue('gradle/gradle-native#206')
    def 'can define and use source repositories with initscript resolution present'() {
        given:
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))
        temporaryFolder.file('initialize.gradle') << """
        initscript {            
            dependencies {
                classpath files('classpath.file')
            }
        }
        allprojects { p ->
            println "Initialization of \${p}"
        }
        """
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble', '-I', 'initialize.gradle')
        // Git repo is cloned
        def gitCheckout = checkoutDir('dep', commit.getId().getName(), "git-repo:${repo.url.toASCIIString()}")
        gitCheckout.file('.git').assertExists()
    }

    @Issue('gradle/gradle-native#207')
    def 'can use repositories even when clean is run'() {
        given:
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """

        expect:
        succeeds('assemble')

        when:
        succeeds('clean', 'assemble')

        then:
        // Git repo is cloned
        def gitCheckout = checkoutDir('dep', commit.getId().getName(), "git-repo:${repo.url.toASCIIString()}")
        gitCheckout.file('.git').assertExists()

    }

    // TODO: Use HTTP hosting for git repo
}
