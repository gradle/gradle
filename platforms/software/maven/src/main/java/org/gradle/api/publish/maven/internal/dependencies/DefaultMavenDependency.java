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
package org.gradle.api.publish.maven.internal.dependencies;

import org.gradle.api.artifacts.ExcludeRule;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Default implementation of {@link MavenDependency}.
 */
public class DefaultMavenDependency implements MavenDependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String classifier;
    private final String scope;
    private final Set<ExcludeRule> excludeRules;
    private final boolean optional;

    public DefaultMavenDependency(
        String groupId,
        String artifactId,
        @Nullable String version,
        @Nullable String type,
        @Nullable String classifier,
        @Nullable String scope,
        Set<ExcludeRule> excludeRules,
        boolean optional
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.classifier = classifier;
        this.scope = scope;
        this.excludeRules = excludeRules;
        this.optional = optional;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Nullable
    @Override
    public String getVersion() {
        return version;
    }

    @Nullable
    @Override
    public String getType() {
        return type;
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    @Nullable
    @Override
    public String getScope() {
        return scope;
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        return excludeRules;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }
}
