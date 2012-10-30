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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

/**
 * A repository of module versions.
 *
 * <p>Current contains a subset of methods from {@link org.apache.ivy.plugins.resolver.DependencyResolver}, while we transition away from it.
 * The plan is to sync with (or replace with) {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver}.
 */
public interface ModuleVersionRepository {
    String getId();

    String getName();

    /**
     * @return null if not found.
     */
    ModuleVersionDescriptor getDependency(DependencyDescriptor dd) throws ModuleVersionResolveException;

    /**
     * Downloads the given artifact. Any failures are packaged up in the result.
     */
    void download(Artifact artifact, BuildableArtifactResolveResult result);

    // TODO - should be internal to the implementation of this (is only used to communicate IvyDependencyResolverAdapter -> CachingModuleVersionRepository)
    boolean isLocal();
}
