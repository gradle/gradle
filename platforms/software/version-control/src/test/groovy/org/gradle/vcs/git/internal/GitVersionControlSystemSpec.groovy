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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.git.GitVersionControlSpec
import org.gradle.vcs.internal.VersionRef
import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Specification

class GitVersionControlSystemSpec extends Specification {
    private GitVersionControlSystem gitVcs
    private GitVersionControlSpec repoSpec
    private VersionRef repoHead
    private RevCommit c1
    private RevCommit c2

    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    GitFileRepository repo = new GitFileRepository(tmpDir.getTestDirectory())
    GitFileRepository repo2 = new GitFileRepository(tmpDir.getTestDirectory().file('other'))
    GitFileRepository submoduleRepo = new GitFileRepository("submodule", tmpDir.testDirectory)
    GitFileRepository submoduleRepo2 = new GitFileRepository("submodule2", tmpDir.testDirectory)

    // Directory clean up needs to happen after all of the repos have closed
    @Rule
    RuleChain rules = RuleChain.outerRule(tmpDir).around(repo).around(repo2).around(submoduleRepo).around(submoduleRepo2)

    def setup() {
        gitVcs = new GitVersionControlSystem()

        // Commit a file to the repository
        def textFile = repo.workTree.file('source.txt')
        textFile << 'Hello world!'
        c1 = repo.commit('Initial commit')
        repo.createBranch('release')
        repo.createLightWeightTag('1.0.1')
        repo.createAnnotatedTag('v1.0.1', 'Release 1.0.1')
        def anotherSource = repo.workTree.file('dir/another.txt')
        anotherSource << 'Goodbye world!'
        c2 = repo.commit('Second Commit')
        repoHead = GitVersionRef.from(repo.head)
        repoSpec = new DefaultGitVersionControlSpec()
        repoSpec.url = repo.url

        submoduleRepo.workTree.file("foo.txt") << "hello from submodule"
        submoduleRepo.commit("initial commit")

        submoduleRepo2.workTree.file("bar.txt") << "hello from another submodule"
        submoduleRepo2.commit("initial commit")
    }

    def 'clone a repository'() {
        given:
        def target = tmpDir.file('versionDir')

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        target.file( '.git').assertIsDir()
        target.file( 'source.txt').text == 'Hello world!'
        target.file( 'dir/another.txt').text == 'Goodbye world!'
    }

    def 'clone a repository with a submodule'() {
        given:
        repo.addSubmodule(submoduleRepo)
        def target = tmpDir.file('versionDir')

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        target.file( '.git').assertIsDir()
        target.file( 'submodule/foo.txt').text == "hello from submodule"
    }

    def 'reset a cloned repository with dirty working dir'() {
        given:
        def target = tmpDir.file('versionDir')
        gitVcs.populate(target, repoHead, repoSpec)

        def removed = target.file("source.txt")
        removed.delete()
        def changed = target.file("dir/another.txt")
        changed << "changed!"

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        removed.text == "Hello world!"
        changed.text == "Goodbye world!"
    }

    // commit() method seems to leak files
    @Requires(UnitTestPreconditions.NotWindows)
    def 'reset a cloned repository with local commits'() {
        given:
        def target = tmpDir.file('versionDir')
        gitVcs.populate(target, repoHead, repoSpec)

        def removed = target.file("source.txt")
        removed.delete()
        def changed = target.file("dir/another.txt")
        changed << "changed!"

        def targetRepo = GitFileRepository.init(target)
        targetRepo.commit('changes')

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        removed.text == "Hello world!"
        changed.text == "Goodbye world!"

        cleanup:
        targetRepo?.close()
    }

    def 'reset a cloned repository with submodules'() {
        given:
        submoduleRepo.addSubmodule(submoduleRepo2)
        repo.addSubmodule(submoduleRepo)

        def target = tmpDir.file('versionDir')
        gitVcs.populate(target, repoHead, repoSpec)

        def submodule = target.file('submodule/foo.txt')
        submodule.text == "changed!"
        def submodule2 = target.file('submodule/submodule2/bar.txt')
        submodule2.text == "changed!"

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        submodule.text == "hello from submodule"
        submodule2.text == "hello from another submodule"
    }

    def 'error if working dir is not a repository'() {
        given:
        def target = tmpDir.file('versionDir')
        target.file('repo').mkdirs()
        target.file('repo/child.txt').createNewFile()

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        thrown GradleException

        when:
        target.file('repo/.git').mkdir()
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        thrown GradleException
    }

    def 'error if working dir repo is missing the remote'() {
        given:
        def target = tmpDir.file('versionDir')
        gitVcs.populate(target, repoHead, repoSpec)

        // Commit a file to the repository
        def textFile = repo2.workTree.file('other.txt')
        textFile << 'Hello world!'
        repo2.commit('Initial Commit')
        repoSpec.url = repo2.url
        repoHead = GitVersionRef.from(repo2.head)

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        thrown GradleException
    }

    def 'error if repo is not a git repo'() {
        given:
        def target = tmpDir.file('versionDir')
        repoSpec.url = 'https://notarepo.invalid'

        when:
        gitVcs.populate(target, repoHead, repoSpec)

        then:
        GradleException e = thrown()
        e.message.contains('Could not clone from https://notarepo.invalid in')
        e.cause != null
        e.cause.message.contains('Exception caught during execution of fetch command')
        e.cause.cause != null
        e.cause.cause.message.contains('URI not supported: https://notarepo.invalid')
    }

    def 'treats tags as the available versions and ignores other references'() {
        given:
        def versions = gitVcs.getAvailableVersions(repoSpec)
        HashMap<String, String> versionMap = Maps.newHashMap()
        for (VersionRef ref : versions) {
            versionMap.put(ref.version, ref.canonicalId)
        }

        expect:
        versions.size() == 2
        versionMap['1.0.1'] == c1.id.name
        versionMap['v1.0.1'] == c1.id.name
    }

    def 'default branch of repo is master'() {
        given:
        def version = gitVcs.getDefaultBranch(repoSpec)

        expect:
        version.version == 'master'
        version.canonicalId == c2.id.name
    }
}
