/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataBuilder;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.MetadataResolutionContext;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class DefaultMetadataProvider implements MetadataProvider {
    private final static Transformer<ComponentMetadata, BuildableComponentMetadataSupplierDetails> TO_COMPONENT_METADATA = BuildableComponentMetadataSupplierDetails::getExecutionResult;
    private final ModuleComponentResolveState resolveState;
    private BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState> cachedResult;
    private ComponentMetadata cachedComponentMetadata;
    private boolean computedMetadata;

    DefaultMetadataProvider(ModuleComponentResolveState resolveState) {
        this.resolveState = resolveState;
    }

    @Override
    public ComponentMetadata getComponentMetadata() {
        if (computedMetadata) {
            return cachedComponentMetadata;
        }

        cachedComponentMetadata = computeMetadata();
        computedMetadata = true;
        return cachedComponentMetadata;
    }

    @Nullable
    private ComponentMetadata computeMetadata() {
        ComponentMetadata metadata = null;
        InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplier = resolveState.getComponentMetadataSupplier();
        if (componentMetadataSupplier != null) {
            metadata = getComponentMetadataFromSupplier(componentMetadataSupplier);
        }
        if (metadata != null) {
            metadata = transformThroughComponentMetadataRules(componentMetadataSupplier, metadata);
        } else if (resolve()) {
            @SuppressWarnings("deprecation")
            ExternalComponentResolveMetadata legacyMetadata = cachedResult.getMetaData().getLegacyMetadata();
            metadata = new ComponentMetadataAdapter(legacyMetadata);
        }
        return metadata;
    }

    private ComponentMetadata transformThroughComponentMetadataRules(InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplier, ComponentMetadata metadata) {
        DefaultMetadataResolutionContext resolutionContext = new DefaultMetadataResolutionContext(resolveState.getCacheExpirationControl(), componentMetadataSupplier.getInstantiator());
        metadata = resolveState.getComponentMetadataProcessorFactory().createComponentMetadataProcessor(resolutionContext).processMetadata(metadata);
        return metadata;
    }

    private ComponentMetadata getComponentMetadataFromSupplier(InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplier) {
        ComponentMetadata metadata;
        ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(resolveState.getId());
        metadata = resolveState.getComponentMetadataSupplierExecutor().execute(id, componentMetadataSupplier, TO_COMPONENT_METADATA, id1 -> {
            final SimpleComponentMetadataBuilder builder = new SimpleComponentMetadataBuilder(id1, resolveState.getAttributesFactory());
            return new BuildableComponentMetadataSupplierDetails(builder);
        }, resolveState.getCacheExpirationControl());
        return metadata;
    }

    @Override
    public IvyModuleDescriptor getIvyModuleDescriptor() {
        if (resolve()) {
            @SuppressWarnings("deprecation")
            ExternalComponentResolveMetadata legacyMetadata = cachedResult.getMetaData().getLegacyMetadata();
            if (legacyMetadata instanceof IvyModuleResolveMetadata) {
                IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) legacyMetadata;
                return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
            }
        }
        return null;
    }

    private boolean resolve() {
        if (cachedResult == null) {
            cachedResult = resolveState.resolve();
        }
        return cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    @Override
    public boolean isUsable() {
        return cachedResult == null || cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    public BuildableModuleComponentMetaDataResolveResult<?> getResult() {
        return cachedResult;
    }

    /**
     * This class bridges from the public type available in metadata suppliers ({@link ComponentMetadataBuilder}
     * to the complete type ({@link ComponentMetadata}) which provides more than what we want to expose in those
     * rules. In particular, the builder exposes setters, that we don't want on the component metadata type.
     */
    private static class SimpleComponentMetadataBuilder implements ComponentMetadataBuilder {
        private final ModuleVersionIdentifier id;
        private boolean mutated; // used internally to determine if a rule effectively did something

        private List<String> statusScheme = ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME;
        private final AttributeContainerInternal attributes;

        private SimpleComponentMetadataBuilder(ModuleVersionIdentifier id, AttributesFactory attributesFactory) {
            this.id = id;
            this.attributes = attributesFactory.mutable();
            this.attributes.attribute(ProjectInternal.STATUS_ATTRIBUTE, MavenVersionUtils.inferStatusFromEffectiveVersion(id.getVersion()));
        }

        @Override
        public void setStatus(String status) {
            attributes.attribute(ProjectInternal.STATUS_ATTRIBUTE, status);
            mutated = true;
        }

        @Override
        public void setStatusScheme(List<String> scheme) {
            this.statusScheme = scheme;
            mutated = true;
        }

        @Override
        public void attributes(Action<? super AttributeContainer> attributesConfiguration) {
            mutated = true;
            attributesConfiguration.execute(attributes);
        }

        @Override
        public AttributeContainer getAttributes() {
            mutated = true;
            return attributes;
        }

        private ImmutableAttributes validateAttributeTypes(AttributeContainerInternal attributes) {
            List<Attribute<?>> invalidAttributes = null;
            for (Attribute<?> attribute : attributes.keySet()) {
                if (!isValidType(attribute)) {
                    if (invalidAttributes == null) {
                        invalidAttributes = new ArrayList<>();
                    }
                    invalidAttributes.add(attribute);
                }
            }
            maybeThrowValidationError(invalidAttributes);
            return attributes.asImmutable();
        }

        private void maybeThrowValidationError(@Nullable List<Attribute<?>> invalidAttributes) {
            if (invalidAttributes != null) {
                TreeFormatter fm = new TreeFormatter();
                fm.node("Invalid attributes types have been provider by component metadata supplier. Attributes must either be strings or booleans");
                fm.startChildren();
                for (Attribute<?> invalidAttribute : invalidAttributes) {
                    fm.node("Attribute '" + invalidAttribute.getName() + "' has type " + invalidAttribute.getType());
                }
                fm.endChildren();
                throw new InvalidUserDataException(fm.toString());
            }
        }

        private static boolean isValidType(Attribute<?> attribute) {
            Class<?> type = attribute.getType();
            return type == String.class || type == Boolean.class || type == Boolean.TYPE;
        }

        ComponentMetadata build() {
            return new UserProvidedMetadata(id, statusScheme, validateAttributeTypes(attributes));
        }

    }

    private class BuildableComponentMetadataSupplierDetails implements ComponentMetadataSupplierDetails {
        private final SimpleComponentMetadataBuilder builder;

        public BuildableComponentMetadataSupplierDetails(SimpleComponentMetadataBuilder builder) {
            this.builder = builder;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return resolveState.getId();
        }

        @Override
        public ComponentMetadataBuilder getResult() {
            return builder;
        }

        @Nullable
        public ComponentMetadata getExecutionResult() {
            if (builder.mutated) {
                return builder.build();
            }
            return null;
        }

    }

    private static class DefaultMetadataResolutionContext implements MetadataResolutionContext {

        private final CacheExpirationControl cacheExpirationControl;
        private final Instantiator instantiator;

        private DefaultMetadataResolutionContext(CacheExpirationControl cacheExpirationControl, Instantiator instantiator) {
            this.cacheExpirationControl = cacheExpirationControl;
            this.instantiator = instantiator;
        }

        @Override
        public CacheExpirationControl getCacheExpirationControl() {
            return cacheExpirationControl;
        }

        @Override
        public Instantiator getInjectingInstantiator() {
            return instantiator;
        }
    }
}
