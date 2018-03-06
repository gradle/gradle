/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.dsl.CapabilitiesHandler;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.CapabilityDescriptor;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ComponentMetadataDetailsAdapter implements ComponentMetadataDetails {
    private final MutableModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser;

    public ComponentMetadataDetailsAdapter(MutableModuleComponentResolveMetadata metadata, Instantiator instantiator,
                                           NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser,
                                           NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser) {
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return metadata.getId();
    }

    @Override
    public boolean isChanging() {
        return metadata.isChanging();
    }

    public String getStatus() {
        return metadata.getStatus();
    }

    @Override
    public List<String> getStatusScheme() {
        return metadata.getStatusScheme();
    }

    @Override
    public void setChanging(boolean changing) {
        metadata.setChanging(changing);
    }

    @Override
    public void setStatus(String status) {
        metadata.setStatus(status);
    }

    @Override
    public void setStatusScheme(List<String> statusScheme) {
        metadata.setStatusScheme(statusScheme);
    }

    @Override
    public void withVariant(String name, Action<? super VariantMetadata> action) {
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, new VariantNameSpec(name), metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void allVariants(Action<? super VariantMetadata> action) {
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, Specs.satisfyAll(), metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void withCapabilities(Action<? super CapabilitiesHandler> action) {
        CapabilitiesAdapter adapter = instantiator.newInstance(CapabilitiesAdapter.class, metadata.getCapabilities());
        action.execute(adapter);
        ImmutableList<? extends CapabilityDescriptor> updatedDescriptors = adapter.getCapabilityDescriptors();
        metadata.setCapabilities(updatedDescriptors);
    }

    @Override
    public ComponentMetadataDetails attributes(Action<? super AttributeContainer> action) {
        AttributeContainer attributes = metadata.getAttributesFactory().mutable((AttributeContainerInternal) metadata.getAttributes());
        action.execute(attributes);
        metadata.setAttributes(attributes);
        return this;
    }

    @Override
    public AttributeContainer getAttributes() {
        return metadata.getAttributes();
    }

    @Override
    public String toString() {
        return metadata.getId().toString();
    }


    private static class VariantNameSpec implements Spec<VariantResolveMetadata> {
        private final String name;

        private VariantNameSpec(String name) {
            this.name = name;
        }

        @Override
        public boolean isSatisfiedBy(VariantResolveMetadata element) {
            return name.equals(element.getName());
        }
    }

    /**
     * This class is public because created using the instantiator, so that we can use the methods without "it." in Groovy.
     */
    public static class CapabilitiesAdapter implements CapabilitiesHandler {
        private final Map<String, MutableCapabilityAdapter> capabilities = Maps.newLinkedHashMap();

        public CapabilitiesAdapter(ImmutableList<? extends CapabilityDescriptor> descriptors) {
            for (CapabilityDescriptor descriptor : descriptors) {
                MutableCapabilityAdapter adapter = new MutableCapabilityAdapter(descriptor.getName(), Lists.newArrayList(descriptor.getProvidedBy()), descriptor.getPrefer(), descriptor.getReason());
                capabilities.put(descriptor.getName(), adapter);
            }
        }

        @Override
        public void capability(String identifier, Action<? super CapabilityHandler> configureAction) {
            MutableCapabilityAdapter handler = capabilities.get(identifier);
            if (handler == null) {
                handler = new MutableCapabilityAdapter(identifier, new ArrayList<String>(), null, null);
                capabilities.put(identifier, handler);
            }
            configureAction.execute(handler);
        }

        public ImmutableList<? extends CapabilityDescriptor> getCapabilityDescriptors() {
            ImmutableList.Builder<DefaultImmutableCapability> builder = new ImmutableList.Builder<DefaultImmutableCapability>();
            for (MutableCapabilityAdapter handler : capabilities.values()) {
                builder.add(new DefaultImmutableCapability(handler.name, ImmutableList.copyOf(handler.providedBy), handler.prefer, handler.reason));
            }
            return builder.build();
        }
    }

    private static class MutableCapabilityAdapter implements CapabilityHandler {

        private final String name;
        private List<String> providedBy;
        private String prefer;
        private String reason;

        private MutableCapabilityAdapter(String name, List<String> providedBy, @Nullable String prefer, @Nullable String reason) {
            this.name = name;
            this.providedBy = providedBy;
            this.prefer = prefer;
            this.reason = reason;
        }

        @Override
        public CapabilityHandler setProvidedBy(Collection<String> moduleIdentifiers) {
            providedBy.clear();
            providedBy.addAll(moduleIdentifiers);
            return this;
        }

        @Override
        public CapabilityHandler providedBy(String moduleIdentifier) {
            providedBy.add(moduleIdentifier);
            return this;
        }

        @Override
        public CapabilityHandler prefer(String moduleIdentifer) {
            this.prefer = moduleIdentifer;
            return this;
        }

        @Override
        public CapabilityHandler because(String reason) {
            this.reason = reason;
            return this;
        }
    }
}
