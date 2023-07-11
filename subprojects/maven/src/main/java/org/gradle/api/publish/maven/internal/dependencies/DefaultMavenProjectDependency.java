/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.maven.internal.dependencies;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collection;

public class DefaultMavenProjectDependency implements MavenDependencyInternal {
    private final MavenDependencyInternal delegate;
    private final Path identityPath;

    public DefaultMavenProjectDependency(MavenDependencyInternal delegate, Path identityPath) {
        this.delegate = delegate;
        this.identityPath = identityPath;
    }

    @Override
    public Collection<DependencyArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public Collection<ExcludeRule> getExcludeRules() {
        return delegate.getExcludeRules();
    }

    @Override
    public Path getProjectIdentityPath() {
        return identityPath;
    }

    @Override
    public String getGroupId() {
        return delegate.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return delegate.getArtifactId();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    @Nullable
    public String getType() {
        return delegate.getType();
    }
}
