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

    def 'can resolve specific version'() {
        given:
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
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))
        repo.createLightWeightTag('1.3.0')

        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.setText(javaFile.text.replace('class', 'interface'))
        repo.commit('Changed Dep to an interface', GFileUtils.listFiles(file('dep'), null, true))

        buildFile.text = buildFile.text.replace('latest.integration', '1.3.0')

        when:
        succeeds('assemble')

        then:
        def gitCheckout = checkoutDir('dep', commit.getId().getName(), "git-repo:${repo.url.toASCIIString()}")
        gitCheckout.file('.git').assertExists()
    }

    def 'handle missing version by adding tag to git repository'() {
        given:
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
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))
        repo.createLightWeightTag('1.3.0')

        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.setText(javaFile.text.replace('class', 'interface'))
        repo.commit('Changed Dep to an interface', GFileUtils.listFiles(file('dep'), null, true))

        buildFile.text = buildFile.text.replace('latest.integration', '1.4.0')

        when:
        fails('assemble')

        then:
        failureCauseContains("does not contain a version matching 1.4.0")

        when:
        javaFile.setText(javaFile.text.replace('interface', 'class'))
        repo.commit('Switch it back to a class.', GFileUtils.listFiles(file('dep'), null, true))
        repo.createLightWeightTag('1.4.0')

        then:
        succeeds('assemble')
    }

    def 'can handle conflicting versions'() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from vcs(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'
            
            dependencies {
                compile "org.test:dep:1.3.0"
                compile "org.test:dep:1.4.0"
            }
        """
        def commit = repo.commit('initial commit', GFileUtils.listFiles(file('dep'), null, true))
        repo.createLightWeightTag('1.3.0')
        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.setText(javaFile.text.replace('class', 'interface'))
        def commit2 = repo.commit('Changed Dep to an interface', GFileUtils.listFiles(file('dep'), null, true))
        repo.createLightWeightTag('1.4.0')

        when:
        succeeds('assemble')

        then:
        def gitCheckout = checkoutDir('dep', commit.getId().getName(), "git-repo:${repo.url.toASCIIString()}")
        gitCheckout.file('.git').assertExists()
        def gitCheckout2 = checkoutDir('dep', commit2.id.name, "git-repo:${repo.url.toASCIIString()}")
        gitCheckout2.file('.git').assertExists()
    }

    // TODO: Use HTTP hosting for git repo
}
