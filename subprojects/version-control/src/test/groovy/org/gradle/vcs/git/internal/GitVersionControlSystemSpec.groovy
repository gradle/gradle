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

import com.google.common.collect.Maps
import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.api.GradleException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.vcs.VersionRef
import org.gradle.vcs.fixtures.GitRepository
import org.gradle.vcs.git.GitVersionControlSpec
import org.junit.Rule
import spock.lang.Specification

class GitVersionControlSystemSpec extends Specification {
    private GitVersionControlSystem gitVcs
    private RevCommit c1
    private RevCommit c2

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    GitRepository repo = new GitRepository(tmpDir)

    @Rule
    GitRepository repo2 = new GitRepository("otherRepo", tmpDir)

    def setup() {
        gitVcs = new GitVersionControlSystem()

        // Commit a file to the repository
        def textFile = repo.workTree.file("source.txt")
        textFile << "Hello world!"
        c1 = repo.commit("Initial commit", textFile)
        repo.createBranch("release")
        repo.createLightWeightTag("1.0.1")
        repo.createAnnotatedTag("v1.0.1", "Release 1.0.1")
        def anotherSource = repo.workTree.file("dir/another.txt")
        anotherSource << "Goodbye world!"
        c2 = repo.commit("Second Commit", anotherSource)
    }

    def "clone a repository"() {
        given:
        def target = tmpDir.file("workingDir")
        GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
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
        GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
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
        GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
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
        GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
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
        GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
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

    def "can get versions"() {
        given:GitVersionControlSpec spec = new DefaultGitVersionControlSpec()
        spec.url = repo.url
        def versions = gitVcs.getAvailableVersions(spec)
        HashMap<String, String> versionMap = Maps.newHashMap()
        for (VersionRef ref : versions) {
            versionMap.put(ref.version, ref.canonicalId)
        }

        expect:
        versions.size() == 5
        versionMap['release'] == c1.id.name
        versionMap['1.0.1'] == c1.id.name
        versionMap['v1.0.1'] == c1.id.name
        versionMap['HEAD'] == c2.id.name
        versionMap['master'] == c2.id.name
    }
}
