/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

class WrappingComponentMetadataContext implements ComponentMetadataContext {


    private final ModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser;

    private MutableModuleComponentResolveMetadata mutableMetadata;
    private ComponentMetadataDetails details;

    public WrappingComponentMetadataContext(ModuleComponentResolveMetadata metadata, Instantiator instantiator,
                                            NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser,
                                            NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser) {
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
    }

    @Override
    public <T> T getDescriptor(Class<T> descriptorClass) {
        if (IvyModuleDescriptor.class.isAssignableFrom(descriptorClass)) {
            if (metadata instanceof IvyModuleResolveMetadata) {
                IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metadata;
                return descriptorClass.cast(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
            }
        }
        return null;
    }

    @Override
    public ComponentMetadataDetails getDetails() {
        createMutableMetadataIfNeeded();
        if (details == null) {
            details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, mutableMetadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);

        }
        return details;
    }

    MutableModuleComponentResolveMetadata getMutableMetadata() {
        createMutableMetadataIfNeeded();
        return mutableMetadata;
    }

    private void createMutableMetadataIfNeeded() {
        if (mutableMetadata == null) {
            mutableMetadata = metadata.asMutable();
        }
    }
}
