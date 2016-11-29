/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.plugin.repository.rules.RuleBasedArtifactRepositories;

import java.util.ArrayList;
import java.util.List;

public class DefaultRuleBasedArtifactRepositories implements RuleBasedArtifactRepositories {

    private final RepositoryHandler repositoryHandler;
    private final List<ArtifactRepository> artifactRepositories = new ArrayList<ArtifactRepository>();

    public DefaultRuleBasedArtifactRepositories(RepositoryHandler repositoryHandler) {
        this.repositoryHandler = repositoryHandler;
    }

    @Override
    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        MavenArtifactRepository maven = repositoryHandler.maven(action);
        artifactRepositories.add(maven);
        return maven;
    }

    @Override
    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        IvyArtifactRepository ivy = repositoryHandler.ivy(action);
        artifactRepositories.add(ivy);
        return ivy;
    }

    public List<ArtifactRepository> getArtifactRepositories() {
        return artifactRepositories;
    }
}
