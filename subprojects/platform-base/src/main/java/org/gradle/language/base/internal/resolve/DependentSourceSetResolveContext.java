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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.model.VariantsMetaData;

public class DependentSourceSetResolveContext implements ResolveContext {
    private final LibraryBinaryIdentifier binaryId;
    private final DependentSourceSetInternal sourceSet;
    private final ResolutionStrategyInternal resolutionStrategy = new DefaultResolutionStrategy();
    private final VariantsMetaData variants;

    public DependentSourceSetResolveContext(LibraryBinaryIdentifier binaryId, DependentSourceSetInternal sourceSet, VariantsMetaData variants) {
        this.binaryId = binaryId;
        this.sourceSet = sourceSet;
        this.variants = variants;
    }

    @Override
    public String getName() {
        return DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME;
    }

    @Override
    public String getDisplayName() {
        return sourceSet.getDisplayName();
    }

    public LibraryBinaryIdentifier getComponentId() {
        return binaryId;
    }

    public DependentSourceSetInternal getSourceSet() {
        return sourceSet;
    }

    public VariantsMetaData getVariants() {
        return variants;
    }

    @Override
    public ResolutionStrategyInternal getResolutionStrategy() {
        return resolutionStrategy;
    }

    @Override
    public DependencySet getDependencies() {
        DefaultDomainObjectSet<Dependency> backingSet = new DefaultDomainObjectSet<Dependency>(Dependency.class);
        return new DefaultDependencySet(String.format("%s dependencies", this.getName()), backingSet);
    }

    @Override
    public DependencySet getAllDependencies() {
        return new DefaultDependencySet(String.format("%s dependencies", this.getName()), new DefaultDomainObjectSet<Dependency>(Dependency.class));
    }
}
