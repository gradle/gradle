/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple concurrent safe cache of {@link ResolvableArtifact} for all projects in a build tree.
 *
 * @see ModuleComponentRepository#getArtifactCache() External dependency resolved artifacts do something similar
 */
@ServiceScope(Scopes.BuildTree.class)
public class ProjectResolvedArtifactCache {
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> allProjectArtifacts = new ConcurrentHashMap<>();

    public Map<ComponentArtifactIdentifier, ResolvableArtifact> getAllProjectArtifacts() {
        return allProjectArtifacts;
    }
}
