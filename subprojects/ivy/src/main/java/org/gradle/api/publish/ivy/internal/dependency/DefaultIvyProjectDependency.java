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
package org.gradle.api.publish.ivy.internal.dependency;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.attributes.ImmutableAttributes;

public class DefaultIvyProjectDependency implements IvyDependencyInternal {
    private final IvyDependencyInternal delegate;
    private final String projectPath;

    public DefaultIvyProjectDependency(IvyDependencyInternal delegate, String projectPath) {
        this.delegate = delegate;
        this.projectPath = projectPath;
    }

    @Override
    public Iterable<DependencyArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public Iterable<ExcludeRule> getExcludeRules() {
        return delegate.getExcludeRules();
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public String getOrganisation() {
        return delegate.getOrganisation();
    }

    @Override
    public String getModule() {
        return delegate.getModule();
    }

    @Override
    public String getRevision() {
        return delegate.getRevision();
    }

    @Override
    public String getConfMapping() {
        return delegate.getConfMapping();
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }
}
