/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.base.Strings;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultIvyDependency implements IvyDependencyInternal {
    private final String organisation;
    private final String module;
    private final String revision;
    private final String confMapping;
    private final boolean transitive;
    private final List<DependencyArtifact> artifacts = new ArrayList<DependencyArtifact>();
    private final List<ExcludeRule> excludeRules = new ArrayList<ExcludeRule>();
    private final ImmutableAttributes attributes;

    public DefaultIvyDependency(String organisation, String module, String revision, String confMapping, boolean transitive) {
        this.organisation = organisation;
        this.module = module;
        this.revision = Strings.nullToEmpty(revision);
        this.confMapping = confMapping;
        this.transitive = transitive;
        this.attributes = ImmutableAttributes.EMPTY;
    }

    public DefaultIvyDependency(String organisation, String module, String revision, String confMapping, boolean transitive, Collection<DependencyArtifact> artifacts) {
        this(organisation, module, revision, confMapping, transitive);
        this.artifacts.addAll(artifacts);
    }

    public DefaultIvyDependency(String organisation, String module, String revision, String confMapping, boolean transitive, Collection<DependencyArtifact> artifacts, Collection<ExcludeRule> excludeRules) {
        this(organisation, module, revision, confMapping, transitive, artifacts);
        this.excludeRules.addAll(excludeRules);
    }

    public DefaultIvyDependency(ExternalDependency dependency, String confMapping, ImmutableAttributes attributes) {
        this.organisation = dependency.getGroup();
        this.module = dependency.getName();
        this.revision = Strings.nullToEmpty(dependency.getVersion());
        this.confMapping = confMapping;
        this.transitive = dependency.isTransitive();
        this.artifacts.addAll(dependency.getArtifacts());
        this.excludeRules.addAll(dependency.getExcludeRules());
        this.attributes = attributes;
    }

    @Override
    public String getOrganisation() {
        return organisation;
    }

    @Override
    public String getModule() {
        return module;
    }

    @Override
    public String getRevision() {
        return revision;
    }

    @Override
    public String getConfMapping() {
        return confMapping;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public Iterable<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public Iterable<ExcludeRule> getExcludeRules() {
        return excludeRules;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public Path getProjectIdentityPath() {
        return null;
    }
}
