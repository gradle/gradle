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

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultMavenDependency implements MavenDependencyInternal {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final List<DependencyArtifact> artifacts = new ArrayList<DependencyArtifact>();
    private final List<ExcludeRule> excludeRules = new ArrayList<ExcludeRule>(); //exclude rules for a dependency specified in gradle DSL

    public DefaultMavenDependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, (String) null);
    }

    public DefaultMavenDependency(String groupId, String artifactId, String version, Collection<DependencyArtifact> artifacts) {
        this(groupId, artifactId, version);
        this.artifacts.addAll(artifacts);
    }

    public DefaultMavenDependency(String groupId, String artifactId, String version, Collection<DependencyArtifact> artifacts, Collection<ExcludeRule> excludeRules) {
        this(groupId, artifactId, version, artifacts);
        this.excludeRules.addAll(excludeRules);
    }

    public DefaultMavenDependency(String groupId, String artifactId, String version, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Nullable
    @Override
    public String getType() {
        return type;
    }

    @Override
    public Collection<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public Collection<ExcludeRule> getExcludeRules() {
        return excludeRules;
    }

    @Override
    public String getProjectPath() {
        return null;
    }
}
