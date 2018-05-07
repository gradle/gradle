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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataBuilder;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.List;

public class MetadataProvider {
    private final ModuleComponentResolveState resolveState;
    private BuildableModuleComponentMetaDataResolveResult cachedResult;
    private Optional<ComponentMetadata> cachedComponentMetadata;

    public MetadataProvider(ModuleComponentResolveState resolveState) {
        this.resolveState = resolveState;
    }

    public MetadataProvider(BuildableModuleComponentMetaDataResolveResult result) {
        this.resolveState = null;
        cachedResult = result;
    }

    public ComponentMetadata getComponentMetadata() {
        if (cachedComponentMetadata != null) {
            return cachedComponentMetadata.orNull();
        }

        ComponentMetadataSupplier componentMetadataSupplier = resolveState == null ? null : resolveState.getComponentMetadataSupplier();
        if (componentMetadataSupplier != null) {
            final SimpleComponentMetadataBuilder builder = new SimpleComponentMetadataBuilder(DefaultModuleVersionIdentifier.newId(resolveState.getId()), resolveState.getAttributesFactory());
            ComponentMetadataSupplierDetails details = new ComponentMetadataSupplierDetails() {
                @Override
                public ModuleComponentIdentifier getId() {
                    return resolveState.getId();
                }

                @Override
                public ComponentMetadataBuilder getResult() {
                    return builder;
                }

            };
            componentMetadataSupplier.execute(details);
            if (builder.mutated) {
                ComponentMetadata metadata = builder.build();
                metadata = resolveState.getComponentMetadataProcessor().processMetadata(metadata);
                cachedComponentMetadata = Optional.of(metadata);
                return metadata;
            }
        }
        if (resolve()) {
            ComponentMetadataAdapter adapter = new ComponentMetadataAdapter(getMetaData());
            cachedComponentMetadata = Optional.<ComponentMetadata>of(adapter);
            return adapter;
        }
        cachedComponentMetadata = Optional.absent();
        return null;
    }

    @Nullable
    public IvyModuleDescriptor getIvyModuleDescriptor() {
        ModuleComponentResolveMetadata metaData = getMetaData();
        if (metaData instanceof IvyModuleResolveMetadata) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metaData;
            return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
        }
        return null;
    }

    public boolean resolve() {
        if (cachedResult == null) {
            cachedResult = resolveState.resolve();
        }
        return cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    public ModuleComponentResolveMetadata getMetaData() {
        resolve();
        return cachedResult.getMetaData();
    }

    public boolean isUsable() {
        return cachedResult == null || cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    @Nullable
    public BuildableModuleComponentMetaDataResolveResult getResult() {
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

        private List<String> statusScheme = ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;
        private final AttributeContainerInternal attributes;

        private SimpleComponentMetadataBuilder(ModuleVersionIdentifier id, ImmutableAttributesFactory attributesFactory) {
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
                        invalidAttributes = Lists.newArrayList();
                    }
                    invalidAttributes.add(attribute);
                }
            }
            maybeThrowValidationError(invalidAttributes);
            return attributes.asImmutable();
        }

        private void maybeThrowValidationError(List<Attribute<?>> invalidAttributes) {
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

}
