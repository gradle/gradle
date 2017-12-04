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

package org.gradle.vcs.git.internal

import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.vcs.fixtures.GitRepository
import org.junit.Rule

import java.util.concurrent.TimeUnit

class VcsCleanupIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitRepository repo = new GitRepository("dep", testDirectory)

    Map<String, RevCommit> commits = [:]

    def setup() {
        repo.workTree.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = file("version").text
            jar {
                from file("version")
            }
        """
        repo.workTree.file("settings.gradle") << """
            rootProject.name = "dep"
        """
        commits["initial"] = repo.commit('initial', repo.workTree)

        def versionFile = repo.workTree.file("version")
        ["1.0", "2.0", "3.0"].each { version ->
            versionFile.text = version
            def commit = repo.commit("version is $version", repo.workTree)
            repo.createLightWeightTag(version)
            commits[version] = commit
        }

        versionFile.text = "4.0-SNAPSHOT"
        commits["latest.integration"] = repo.commit("version is snapshot", repo.workTree)

        buildFile << """
            apply plugin: 'base'
            
            configurations {
                conf
            }
            dependencies {
                conf "org.test:dep:" + repoVersion
            }
            task assertVersion {
                dependsOn configurations.conf
                doLast {
                    def files = zipTree(configurations.conf.singleFile).files
                    def versionFile = files.find { it.name == "version" }
                    assert versionFile
                    assert versionFile.text == repoVersion
                }
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
    }

    def "does not remove vcs checkout on every build"() {
        succeeds("assertVersion", "-PrepoVersion=1.0")
        def checkout = checkoutDir("dep", commits.initial.id.name, repo.id)

        // Put new file in the checkout directory that would be deleted if Gradle
        // deletes the checkout directory
        def trashFile = checkout.file("trash")
        trashFile.text = "junk"

        when:
        succeeds("assertVersion", "-PrepoVersion=1.0")
        then:
        trashFile.assertExists()

        when:
        cleanupNow()
        succeeds("assertVersion", "-PrepoVersion=1.0")
        then:
        // checkout was used recently, so it should still be kept around
        trashFile.assertExists()
    }

    def "removes vcs checkout after 7 days"() {
        succeeds("assertVersion", "-PrepoVersion=1.0")
        succeeds("assertVersion", "-PrepoVersion=2.0")
        succeeds("assertVersion", "-PrepoVersion=3.0")

        def checkout = checkoutDir("dep", commits["1.0"].id.name, repo.id).parentFile
        checkout.setLastModified(checkout.lastModified() - TimeUnit.DAYS.toMillis(10))

        when:
        cleanupNow()
        succeeds("assertVersion", "-PrepoVersion=3.0")
        then:
        checkoutDir("dep", commits["1.0"].id.name, repo.id).assertDoesNotExist()
        checkoutDir("dep", commits["2.0"].id.name, repo.id).assertExists()
        checkoutDir("dep", commits["3.0"].id.name, repo.id).assertExists()
    }

    def "does not remove vcs checkout that is older than 7 days but recently used"() {
        def versions = ["1.0", "2.0", "3.0"]
        // checkout all versions
        versions.each { version ->
            succeeds("assertVersion", "-PrepoVersion=${version}")
        }
        // mark versions as unused
        versions.each { version ->
            def checkout = checkoutDir("dep", commits[version].id.name, repo.id).parentFile
            checkout.setLastModified(checkout.lastModified() - TimeUnit.DAYS.toMillis(10))
        }

        when:
        cleanupNow()
        succeeds("assertVersion", "-PrepoVersion=1.0")
        then:
        checkoutDir("dep", commits["1.0"].id.name, repo.id).assertExists()
        checkoutDir("dep", commits["2.0"].id.name, repo.id).assertDoesNotExist()
        checkoutDir("dep", commits["3.0"].id.name, repo.id).assertDoesNotExist()
    }

    TestFile checkoutDir(String repoName, String versionId, String repoId, TestFile baseDir=testDirectory) {
        def hashedRepo = hashRepositoryId(repoId)
        baseDir.file(".gradle/vcsWorkingDirs/${hashedRepo}-${versionId}/${repoName}")
    }

    String hashRepositoryId(String repoId) {
        HashUtil.createCompactMD5(repoId)
    }

    TestFile gcFile() {
        file(".gradle/vcsWorkingDirs/gc.properties")
    }

    void cleanupNow() {
        gcFile().assertIsFile()
        gcFile().lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)
    }

}
