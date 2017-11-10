/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * An edge in the dependency graph, between 2 nodes.
 */
public interface DependencyGraphEdge extends DependencyResult {
    DependencyGraphNode getFrom();

    DependencyGraphSelector getSelector();

    ModuleExclusion getExclusions(ModuleExclusions moduleExclusions);

    Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata metaData);

    @Nullable
    ModuleDependency getModuleDependency();

    Iterable<? extends DependencyGraphNode> getTargets();
}
