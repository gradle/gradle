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

import com.google.common.collect.Sets;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.Named;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.CollectionUtils;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;

public class GitRepository extends ExternalResource implements Named {
    private final String repoName;
    private final File parentDirectory;
    private Git git;

    public GitRepository(String repoName, File parentDirectory) {
        this.repoName = repoName;
        this.parentDirectory = parentDirectory;
    }

    public GitRepository(File parentDirectory) {
        this("repo", parentDirectory);
    }

    @Override
    public String getName() {
        return repoName;
    }

    /**
     * Factory method for creating a GitRepository without using a JUnit @Rule.
     *
     * Creates a repository in the given repoDir.
     */
    public static GitRepository init(File repoDir) throws GitAPIException {
        GitRepository repo = new GitRepository(repoDir);
        repo.createGitRepo(repoDir);
        return repo;
    }

    @Override
    protected void before() throws Throwable {
        createGitRepo(new File(parentDirectory, repoName));
    }

    private void createGitRepo(File repoDir) throws GitAPIException {
        git = Git.init().setDirectory(repoDir).call();
    }

    @Override
    protected void after() {
        close();
    }

    /**
     * Clean up any held resources
     * (called automatically when used as a @Rule)
     */
    public void close() {
        git.close();
    }

    public RevCommit commit(String message, Collection<File> files) throws GitAPIException {
        AddCommand add = git.add();
        for (File file : files) {
            add.addFilepattern(relativePath(file));
        }
        add.call();
        return git.commit().setMessage(message).call();
    }

    public RevCommit commit(String message, File... files) throws GitAPIException {
        return commit(message, Arrays.asList(files));
    }

    public Ref createBranch(String branchName) throws GitAPIException {
        return git.branchCreate().setName(branchName).call();
    }

    public Ref createLightWeightTag(String tagName) throws GitAPIException {
        return git.tag().setName(tagName).call();
    }

    public Ref createAnnotatedTag(String tagName, String message) throws GitAPIException {
        return git.tag().setName(tagName).setAnnotated(true).setMessage(message).call();
    }

    public Ref getHead() throws IOException {
        return git.getRepository().findRef("HEAD");
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

    public Collection<TestFile> listFiles() throws GitAPIException {
        Status status = git.status().call();
        Collection<TestFile> files = Sets.newHashSet();
        Transformer<TestFile, String> relativePathToTestFile = new Transformer<TestFile, String>() {
            @Override
            public TestFile transform(String path) {
                return getWorkTree().file(path);
            }
        };
        CollectionUtils.collect(status.getUncommittedChanges(), files, relativePathToTestFile);
        CollectionUtils.collect(status.getUntracked(), files, relativePathToTestFile);
        return files;
    }

    public String getId() {
        try {
            return "git-repo:" + getUrl().toASCIIString();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
