/*
 * Copyright 2018 the original author or authors.
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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.test.fixtures.file.TestFile;

import java.net.URI;

public interface GitRepository {
    URI getUrl();

    Ref createBranch(String branchName) throws GitAPIException;

    Ref checkout(String branchName) throws GitAPIException;

    Ref createLightWeightTag(String tagName) throws GitAPIException;

    TestFile getWorkTree();

    TestFile file(Object... path);

    RevCommit commit(String message) throws GitAPIException;
}
