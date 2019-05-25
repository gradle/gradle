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

package org.gradle.plugins.javascript.base;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout;
import org.gradle.internal.Actions;

public class JavaScriptRepositoriesExtension {

    public static final String NAME = "javaScript";

    public static final String GRADLE_PUBLIC_JAVASCRIPT_REPO_URL = "https://repo.gradle.org/gradle/javascript-public";
    public static final String GOOGLE_APIS_REPO_URL = "https://ajax.googleapis.com/ajax/libs";

    private final RepositoryHandler repositories;

    public JavaScriptRepositoriesExtension(RepositoryHandler repositories) {
        this.repositories = repositories;
    }

    public ArtifactRepository gradle() {
        return gradle(Actions.doNothing());
    }

    public MavenArtifactRepository gradle(final Action<? super MavenArtifactRepository> action) {
        return repositories.maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository repository) {
                repository.setName("gradleJs");
                repository.setUrl(GRADLE_PUBLIC_JAVASCRIPT_REPO_URL);
                repository.metadataSources(new Action<MavenArtifactRepository.MetadataSources>() {
                    @Override
                    public void execute(MavenArtifactRepository.MetadataSources metadataSources) {
                        metadataSources.artifact();
                    }
                });
                action.execute(repository);
            }
        });
    }

    public IvyArtifactRepository googleApis() {
        return googleApis(Actions.doNothing());
    }

    public IvyArtifactRepository googleApis(final Action<? super IvyArtifactRepository> action) {
        return repositories.ivy(new Action<IvyArtifactRepository>() {
            @Override
            public void execute(IvyArtifactRepository repo) {
                repo.setName("googleApisJs");
                repo.setUrl(GOOGLE_APIS_REPO_URL);
                repo.patternLayout(new Action<IvyPatternRepositoryLayout>() {
                    @Override
                    public void execute(IvyPatternRepositoryLayout layout) {
                        layout.artifact("[organization]/[revision]/[module].[ext]");
                        layout.ivy("[organization]/[revision]/[module].xml");
                    }
                });
                repo.metadataSources(new Action<IvyArtifactRepository.MetadataSources>() {
                    @Override
                    public void execute(IvyArtifactRepository.MetadataSources metadataSources) {
                        metadataSources.artifact();
                    }
                });
                action.execute(repo);
            }
        });
    }

}
