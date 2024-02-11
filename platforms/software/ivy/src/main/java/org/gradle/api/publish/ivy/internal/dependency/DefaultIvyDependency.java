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

import javax.annotation.Nullable;
import java.util.Set;

public class DefaultIvyDependency implements IvyDependency {
    private final String organisation;
    private final String module;
    private final String revision;
    private final String confMapping;
    private final boolean transitive;
    private final String revConstraint;
    private final Set<DependencyArtifact> artifacts;
    private final Set<ExcludeRule> excludeRules;

    public DefaultIvyDependency(
        String organisation,
        String module,
        String revision,
        String confMapping,
        boolean transitive,
        @Nullable String revConstraint,
        Set<DependencyArtifact> artifacts,
        Set<ExcludeRule> excludeRules
    ) {
        this.organisation = organisation;
        this.module = module;
        this.revision = Strings.nullToEmpty(revision);
        this.confMapping = confMapping;
        this.transitive = transitive;
        this.revConstraint = revConstraint;
        this.excludeRules = excludeRules;
        this.artifacts = artifacts;
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

    @Nullable
    @Override
    public String getRevConstraint() {
        return revConstraint;
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
    public Set<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        return excludeRules;
    }
}
