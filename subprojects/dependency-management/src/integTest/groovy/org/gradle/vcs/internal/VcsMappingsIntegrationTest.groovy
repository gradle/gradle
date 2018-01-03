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

import org.gradle.vcs.internal.spec.DirectoryRepositorySpec

class VcsMappingsIntegrationTest extends AbstractVcsIntegrationTest {
    def setup() {
        settingsFile << """
            import ${DirectoryRepositorySpec.canonicalName}
        """
    }

    def "can define and use source repositories"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def 'emits sensible error when bad code is in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule('rule') { details ->
                        foo()
                    }
                }
            }
        """
        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasFileName("Settings file '$settingsFile.path'")
        failure.assertHasLineNumber(7)
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not find method foo()")
    }

    def 'emits sensible error when bad vcs url in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(GitVersionControlSpec) {
                            url = 'https://bad.invalid'
                        }
                    }
                }
            }
        """

        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not list available versions for 'Git Repository at https://bad.invalid'.")
    }

    def "can define and use source repositories with all {}"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule("rule") { details ->
                        if (details.requested.group == "org.test") {
                            from vcs(DirectoryRepositorySpec) {
                                sourceDir = file("dep")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def "can define unused vcs mappings"() {
        settingsFile << """
            // include the missing dep as a composite
            includeBuild 'dep'
            
            sourceControl {
                vcsMappings {
                    withModule("unused:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    addRule("rule") { details ->
                        if (details instanceof ModuleVersionSelector && details.requested.group == "unused") {
                            from vcs(DirectoryRepositorySpec) {
                                sourceDir = file("does-not-exist")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoNotCheckedOut()
        assertRepoNotCheckedOut("does-not-exist")
    }

    def "last vcs mapping rule wins"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
        assertRepoNotCheckedOut("does-not-exist")
    }

    def 'missing settings has clear error'() {
        file('dep/settings.gradle').delete()
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        assertRepoCheckedOut()
        failureCauseContains("Included build from '")
        failureCauseContains("' must contain a settings file.")
    }

    def 'main build can request plugins to be applied to source dependency build'() {
        depProject.settingsFile.delete()
        depProject.buildFile.delete()

        singleProjectBuild("buildSrc") {
            file("src/main/groovy/MyPlugin.groovy") << """
                import org.gradle.api.*
                import org.gradle.api.initialization.*
                
                class MyPlugin implements Plugin<Settings> {
                    void apply(Settings settings) {
                        settings.gradle.allprojects {
                            apply plugin: 'java'
                            group = 'org.test'
                            version = '1.0'
                        }
                    }
                }
            """
            file("src/main/resources/META-INF/gradle-plugins/com.example.MyPlugin.properties") << """
                implementation-class=MyPlugin
            """
        }

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                            plugins {
                                id "com.example.MyPlugin"
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'can build from sub-directory of repository'() {
        file('repoRoot').mkdir()
        file('dep').renameTo(file('repoRoot/dep'))
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file('repoRoot')
                            rootDir = 'dep'
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut('repoRoot')
    }

    def 'fails with a reasonable message if rootDir is invalid'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file('dep')
                            rootDir = null
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        result.error.contains("rootDir should be non-null")
    }

    void assertRepoCheckedOut(String repoName="dep") {
        def checkout = checkoutDir(repoName, "fixed", "directory-repo:${file(repoName).absolutePath}")
        checkout.file("checkedout").assertIsFile()
    }

    void assertRepoNotCheckedOut(String repoName="dep") {
        def checkout = checkoutDir(repoName, "fixed", "directory-repo:${file(repoName).absolutePath}")
        checkout.file("checkedout").assertDoesNotExist()
    }
}
