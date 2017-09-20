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

import org.gradle.api.GradleException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.vcs.fixtures.TemporaryGitRepository
import org.gradle.vcs.git.GitVersionControlSpec
import org.junit.Rule
import spock.lang.Specification

class GitVersionControlSystemSpec extends Specification {
    private GitVersionControlSystem gitVcs

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    TemporaryGitRepository repo = new TemporaryGitRepository(tmpDir)

    @Rule
    TemporaryGitRepository repo2 = new TemporaryGitRepository("otherRepo", tmpDir)

    def setup() {
        gitVcs = new GitVersionControlSystem()

        // Commit a file to the repository
        def textFile = repo.workTree.file("source.txt")
        textFile << "Hello world!"
        def anotherSource = repo.workTree.file("dir/another.txt")
        anotherSource << "Goodbye world!"
        repo.commit("Initial Commit", textFile, anotherSource)
    }

    def "clone a repository"() {
        given:
        def target = tmpDir.file("workingDir")
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = repo.url

        when:
        gitVcs.populate(target, spec)

        then:
        target.file( ".git").assertIsDir()
        target.file( "source.txt").text == "Hello world!"
        target.file( "dir/another.txt").text == "Goodbye world!"
    }

    def "clone a repository into empty extant workingDir"() {
        given:
        def target = tmpDir.file("workingDir")
        target.mkdir()
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = repo.url

        when:
        gitVcs.populate(target, spec)

        then:
        target.file( ".git").assertIsDir()
        target.file( "source.txt").text == "Hello world!"
        target.file( "dir/another.txt").text == "Goodbye world!"
    }

    def "update a cloned repository"() {
        given:
        def target = tmpDir.file("workingDir")
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = repo.url
        gitVcs.populate(target, spec)
        def newFile = repo.workTree.file("newFile.txt")
        newFile << "I'm new!"
        repo.commit("Add newFile.txt", newFile)

        expect:
        !target.file("newFile.txt").exists()

        when:
        gitVcs.populate(target, spec)

        then:
        target.file("newFile.txt").exists()
    }

    def "error if working dir is not a repository"() {
        given:
        def target = tmpDir.file("workingdir")
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = repo.url
        target.mkdirs()
        target.file("child.txt").createNewFile()

        when:
        gitVcs.populate(target, spec)

        then:
        thrown GradleException

        when:
        target.file(".git").mkdir()
        gitVcs.populate(target, spec)

        then:
        thrown GradleException
    }

    def "error if working dir repo is missing the remote"() {
        given:
        def target = tmpDir.file("workingDir")
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = repo.url
        gitVcs.populate(target, spec)

        // Commit a file to the repository
        def textFile = repo2.workTree.file("other.txt")
        textFile << "Hello world!"
        repo2.commit("Initial Commit", textFile)
        spec.url = repo2.url

        when:
        gitVcs.populate(target, spec)

        then:
        thrown GradleException
    }
}
