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

/**
 * A repository of module versions.
 *
 * The plan is to sync this with {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver}
 */
public interface ModuleVersionRepository {
    String getId();

    String getName();

    void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionDescriptor result);

    /**
     * Downloads the given artifact. Any failures are packaged up in the result.
     */
    void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource);

    // TODO - should be internal to the implementation of this (is only used to communicate IvyDependencyResolverAdapter -> CachingModuleVersionRepository)
    boolean isLocal();
}
