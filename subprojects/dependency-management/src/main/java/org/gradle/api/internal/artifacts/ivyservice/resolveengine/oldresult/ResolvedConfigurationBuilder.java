/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.component.model.ComponentArtifactMetaData;

import java.util.Set;

//builds old model of resolved dependency graph based on the result events
public interface ResolvedConfigurationBuilder {

    void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedConfigurationIdentifier dependency);

    void addUnresolvedDependency(UnresolvedDependency unresolvedDependency);

    void addChild(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child);

    void done(ResolvedConfigurationIdentifier root);

    void addParentSpecificArtifacts(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, Set<ResolvedArtifact> artifacts);

    void newResolvedDependency(ResolvedConfigurationIdentifier id);

    ResolvedArtifact newArtifact(ResolvedConfigurationIdentifier owner, ComponentResolveMetaData component, ComponentArtifactMetaData artifact, ArtifactResolver artifactResolver);
}
