/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;

/**
 * Builds the root component to use as the root of a dependency graph.
 */
public interface RootComponentMetadataBuilder {

    /**
     * Build the component, caching the result. Then return the component and the variant with the given name.
     */
    RootComponentState toRootComponent(String configurationName);

    /**
     * Create a new builder, that builds a new component with a new identity and configuration set
     */
    RootComponentMetadataBuilder newBuilder(DependencyMetaDataProvider identity, ConfigurationsProvider provider);

    /**
     * Get the identity of the component built by this builder.
     */
    DependencyMetaDataProvider getComponentIdentity();

    /**
     * Should be notified when the configuration container that this builder uses is modified.
     */
    MutationValidator getValidator();

    interface RootComponentState {
        LocalComponentGraphResolveState getRootComponent();

        LocalVariantGraphResolveState getRootVariant();
    }
}
