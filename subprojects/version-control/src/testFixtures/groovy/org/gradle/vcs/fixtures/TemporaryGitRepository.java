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

package org.gradle.vcs.fixtures;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.internal.UncheckedException;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class TemporaryGitRepository extends ExternalResource {
    private final String repoName;
    private final TestDirectoryProvider temporaryFolder;
    private Git git;

    public TemporaryGitRepository(String repoName, TestDirectoryProvider temporaryFolder) {
        this.repoName = repoName;
        this.temporaryFolder = temporaryFolder;
    }

    public TemporaryGitRepository(TestDirectoryProvider temporaryFolder) {
        this("repo", temporaryFolder);
    }

    @Override
    protected void before() throws Throwable {
        git = Git.init().setDirectory(temporaryFolder.getTestDirectory().file(repoName)).call();
    }

    @Override
    protected void after() {
        git.close();
    }

    public void commit(String message, File... files) throws GitAPIException {
        AddCommand add = git.add();
        for (File file : files) {
            add.addFilepattern(relativePath(file));
        }
        add.call();
        git.commit().setMessage(message).call();
    }

    public TestFile getWorkTree() {
        return new TestFile(git.getRepository().getWorkTree());
    }

    public URI getUrl() throws URISyntaxException {
        return getWorkTree().toURI();
    }

    private String relativePath(File file) {
        try {
            return getUrl().relativize(file.toURI()).toString();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
