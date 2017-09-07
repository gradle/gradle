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

package org.gradle.vcs.git

import org.gradle.test.fixtures.file.TestFile
import org.gradle.vcs.fixtures.TemporaryGitRepository
import org.junit.Rule
import spock.lang.Specification

class GitVersionControlSystemSpec extends Specification {
    private GitVersionControlSystem gitVcs

    @Rule
    TemporaryGitRepository r = new TemporaryGitRepository()

    def setup() {
        gitVcs = new GitVersionControlSystem()

        // Commit a file to the repository
        def textFile = new TestFile(r.getWorkTree(), "source.txt")
        textFile << "Hello world!"
        r.commit("Initial Commit", textFile)
    }

    def "clone a repository"() {
        given:
        File target = r.createTempDirectory("cloneTarget")
        GitVersionControlSpec spec = new GitVersionControlSpec()
        spec.url = r.getUrl()

        when:
        gitVcs.populate(target, spec)

        then:
        new File(target, ".git").exists()
        new File(target, "source.txt").exists()
    }
}
