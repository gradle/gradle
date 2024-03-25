/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.List;

@ServiceScope(Scope.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;
    private final LocalVariantMetadataBuilder metadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    @Inject
    public LocalComponentGraphResolveStateFactory(
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        LocalVariantMetadataBuilder metadataBuilder,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.attributeDesugaring = attributeDesugaring;
        this.idGenerator = idGenerator;
        this.metadataBuilder = metadataBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public LocalComponentGraphResolveState stateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ConfigurationsProvider configurations,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema
    ) {
        return lazyStateFor(model, componentIdentifier, configurations, moduleVersionId, status, schema, false);
    }

    /**
     * Creates state for an ad hoc component, see {@link org.gradle.internal.component.model.ComponentGraphResolveState#isAdHoc()} for the definition of "ad hoc component".
     */
    public LocalComponentGraphResolveState adHocStateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ConfigurationsProvider configurations,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema
    ) {
        return lazyStateFor(model, componentIdentifier, configurations, moduleVersionId, status, schema, true);
    }

    /**
     * Creates state for a component loaded from the configuration cache.
     */
    public LocalComponentGraphResolveState realizedStateFor(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        List<? extends LocalVariantGraphResolveMetadata> variants
    ) {
        DefaultLocalComponentGraphResolveMetadata.VariantMetadataFactory variantsFactory =
            new DefaultLocalComponentGraphResolveMetadata.VariantsMapMetadataFactory(variants);

        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, false, variantsFactory);
    }

    private LocalComponentGraphResolveState lazyStateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ConfigurationsProvider configurations,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc
    ) {
        DefaultLocalComponentGraphResolveMetadata.VariantMetadataFactory variantsFactory =
            new DefaultLocalComponentGraphResolveMetadata.ConfigurationsProviderMetadataFactory(
                componentIdentifier,
                configurations,
                metadataBuilder,
                model,
                calculatedValueContainerFactory
            );

        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, adHoc, variantsFactory);
    }

    private DefaultLocalComponentGraphResolveState createLocalComponentState(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc,
        DefaultLocalComponentGraphResolveMetadata.VariantMetadataFactory configurationMetadataFactory
    ) {
        LocalComponentGraphResolveMetadata metadata = new DefaultLocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            schema,
            configurationMetadataFactory,
            null
        );

        return new DefaultLocalComponentGraphResolveState(idGenerator.nextComponentId(), metadata, attributeDesugaring, idGenerator, adHoc);
    }
}
