/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.vcs.fixtures

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class GitFileRepositoryTest extends Specification {
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "can create a repository and ignore .gitconfig"() {
        given:
        System.properties['user.home'] = tmpDir
        tmpDir.file(".gitconfig") << """
[init]
defaultBranch = foobar
"""
        def repoDir = tmpDir.createDir("repo")
        GitFileRepository repo = GitFileRepository.init(repoDir)
        repo.commit("initial commit")

        expect:
        def refs = repo.git.branchList().call()
        refs.size() == 1
        // Ignores default branch name "foobar" in .gitconfig
        refs[0].getName() == "refs/heads/master"
    }
}
