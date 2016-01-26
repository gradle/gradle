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
package org.gradle.language.base.internal.resolve;

import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.internal.component.local.model.UsageKind;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.platform.base.DependencySpec;

import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData.newResolvingLocalComponentMetadata;

public class LocalComponentResolveContext implements ResolveContext {
    private final LibraryBinaryIdentifier libraryBinaryIdentifier;
    private final String displayName;
    private final UsageKind usage;
    private final ResolutionStrategyInternal resolutionStrategy = new DefaultResolutionStrategy();
    private final VariantsMetaData variants;
    private final Iterable<DependencySpec> dependencies;

    public LocalComponentResolveContext(
        LibraryBinaryIdentifier libraryBinaryIdentifier,
        VariantsMetaData variants,
        Iterable<DependencySpec> dependencies,
        UsageKind usage,
        String displayName) {
        this.libraryBinaryIdentifier = libraryBinaryIdentifier;
        this.usage = usage;
        this.displayName = displayName;
        this.variants = variants;
        this.dependencies = dependencies;
    }

    @Override
    public String getName() {
        return usage.getConfigurationName();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public VariantsMetaData getVariants() {
        return variants;
    }

    @Override
    public ResolutionStrategyInternal getResolutionStrategy() {
        return resolutionStrategy;
    }

    @Override
    public ComponentResolveMetaData toRootComponentMetaData() {
        return newResolvingLocalComponentMetadata(libraryBinaryIdentifier, usage, dependencies);
    }

}
