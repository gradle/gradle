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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;

import java.util.Set;

public interface ResolvedConfigurationBuilder {

    //not needed
    void addArtifact(ResolvedArtifact artifact);

    void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency dependency);

    void addUnresolvedDependency(UnresolvedDependency unresolvedDependency);

    void addChild(ResolvedDependency parent, ResolvedDependency child);

    void addParentSpecificArtifacts(ResolvedDependency parent, ResolvedDependency child, Set<ResolvedArtifact> artifacts);

    ResolvedDependency newResolvedDependency(ModuleVersionIdentifier id, String name);

    ResolvedArtifact newArtifact(ResolvedDependency result, Artifact artifact, ArtifactResolver artifactResolver);
}
