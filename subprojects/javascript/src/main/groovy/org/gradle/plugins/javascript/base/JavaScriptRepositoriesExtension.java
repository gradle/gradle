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
    public static final String GOOGLE_APIS_REPO_URL = "http://ajax.googleapis.com/ajax/libs";

    private final RepositoryHandler repositories;

    public JavaScriptRepositoriesExtension(RepositoryHandler repositories) {
        this.repositories = repositories;
    }

    public ArtifactRepository gradle() {
        return gradle(Actions.doNothing());
    }

    public MavenArtifactRepository gradle(final Action<? super MavenArtifactRepository> action) {
        return repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository repository) {
                repository.setName("gradleJs");
                repository.setUrl(GRADLE_PUBLIC_JAVASCRIPT_REPO_URL);
                action.execute(repository);
            }
        });
    }

    public IvyArtifactRepository googleApis() {
        return googleApis(Actions.doNothing());
    }

    public IvyArtifactRepository googleApis(final Action<? super IvyArtifactRepository> action) {
        return repositories.ivy(new Action<IvyArtifactRepository>() {
            public void execute(IvyArtifactRepository repo) {
                repo.setName("googleApisJs");
                repo.setUrl(GOOGLE_APIS_REPO_URL);
                repo.layout("pattern", new Action<IvyPatternRepositoryLayout>() {
                    public void execute(IvyPatternRepositoryLayout layout) {
                        layout.artifact("[organization]/[revision]/[module].[ext]");
                        layout.ivy("[organization]/[revision]/[module].xml");
                    }
                });
                action.execute(repo);
            }
        });
    }

}
