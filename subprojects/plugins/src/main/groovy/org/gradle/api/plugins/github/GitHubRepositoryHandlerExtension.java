/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.github;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.internal.Factory;

/**
 * Provides GitHub oriented dependency repository notations.
 */
@Incubating
public class GitHubRepositoryHandlerExtension {

    private final RepositoryHandler repositories;
    private final Factory<GitHubDownloadsRepository> downloadsRepositoryFactory;

    public GitHubRepositoryHandlerExtension(RepositoryHandler repositories, Factory<GitHubDownloadsRepository> downloadsRepositoryFactory) {
        this.repositories = repositories;
        this.downloadsRepositoryFactory = downloadsRepositoryFactory;
    }

    public GitHubDownloadsRepository downloads(final String user) {
        return downloads(new Action<GitHubDownloadsRepository>() {
            public void execute(GitHubDownloadsRepository gitHubDownloadsRepository) {
                gitHubDownloadsRepository.setUser(user);
            }
        });
    }

    public GitHubDownloadsRepository downloads(Action<GitHubDownloadsRepository> configure) {
        GitHubDownloadsRepository repository = downloadsRepositoryFactory.create();
        configure.execute(repository);
        repositories.add(repository);
        return repository;
    }

}
