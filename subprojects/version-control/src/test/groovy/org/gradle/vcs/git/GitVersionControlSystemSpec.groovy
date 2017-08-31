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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.junit.TestRepository
import org.eclipse.jgit.lib.Repository
import org.gradle.vcs.fixtures.TemporaryGitRepository
import org.junit.Rule
import spock.lang.Specification

class GitVersionControlSystemSpec extends Specification {
    private GitVersionControlSystem gitVcs

    private Git git

    private TestRepository<Repository> tr

    @Rule
    TemporaryGitRepository r = new TemporaryGitRepository()

    def setup() {
        gitVcs = new GitVersionControlSystem()
        git = new Git(r.getDatabase())
        tr = new TestRepository<>(r.getDatabase())

        // Commit a file to the repository
        r.writeTrashFile("Trash.txt", "Hello world!")
        git.add().addFilepattern("Trash.txt").call()
        git.commit().setMessage("Initial Commit").call()
    }

    def "clone a repository"() {
        given:
        File target = r.createTempDirectory("cloneTarget")
        GitVersionControlSpec spec = new GitVersionControlSpec().setUrl(new URI("file://" + r.getTrash().getAbsolutePath()))

        when:
        gitVcs.populate(target, spec)

        then:
        new File(target, ".git").exists()
        new File(target, "Trash.txt").exists()
    }
}
