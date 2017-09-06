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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class TemporaryGitRepository extends RepositoryTestCase implements TestRule {
    private TestRepository<Repository> tr;
    private Git git;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tr = new TestRepository<Repository>(db);
        git = tr.git();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setUp();
                base.evaluate();
                tearDown();
            }
        };
    }

    public void commit(String message, File... files) throws GitAPIException {
        for (File file : files) {
            git.add().addFilepattern(file.getName()).call();
        }
        git.commit().setMessage(message).call();
    }

    public File getWorkTree() {
        return tr.getRepository().getWorkTree();
    }

    public URI getUrl() throws URISyntaxException {
        return new URI("file://" + tr.getRepository().getWorkTree().getAbsolutePath());
    }

    @Override
    public File createTempDirectory(String name) throws IOException {
        return super.createTempDirectory(name);
    }
}
