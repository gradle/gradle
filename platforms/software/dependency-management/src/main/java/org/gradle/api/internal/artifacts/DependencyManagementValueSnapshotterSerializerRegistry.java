/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CapabilitySerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedComponentResultSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedVariantResultSerializer;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.ComponentFileArtifactIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.ModuleComponentFileArtifactIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.PublishArtifactLocalArtifactMetadataSerializer;
import org.gradle.api.internal.artifacts.metadata.TransformedComponentFileArtifactIdentifierSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;

import java.io.File;
import java.util.List;

public class DependencyManagementValueSnapshotterSerializerRegistry extends DefaultSerializerRegistry implements ValueSnapshotterSerializerRegistry {

    private static final List<Class<?>> SUPPORTED_TYPES = ImmutableList.of(
        Capability.class,
        ModuleVersionIdentifier.class,
        PublishArtifactLocalArtifactMetadata.class,
        OpaqueComponentArtifactIdentifier.class,
        DefaultModuleComponentArtifactIdentifier.class,
        ModuleComponentFileArtifactIdentifier.class,
        ComponentFileArtifactIdentifier.class,
        ComponentIdentifier.class,
        AttributeContainer.class,
        ResolvedVariantResult.class,
        ComponentSelectionDescriptor.class,
        ComponentSelectionReason.class,
        ComponentSelector.class,
        ResolvedComponentResult.class
    );

    public DependencyManagementValueSnapshotterSerializerRegistry(
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        NamedObjectInstantiator namedObjectInstantiator,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory
    ) {
        super(true);

        ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();
        AttributeContainerSerializer attributeContainerSerializer = new DesugaringAttributeContainerSerializer(immutableAttributesFactory, namedObjectInstantiator);
        ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);

        register(Capability.class, new CapabilitySerializer());
        register(ModuleVersionIdentifier.class, moduleVersionIdentifierSerializer);
        register(PublishArtifactLocalArtifactMetadata.class, new PublishArtifactLocalArtifactMetadataSerializer(componentIdentifierSerializer));
        register(OpaqueComponentArtifactIdentifier.class, new OpaqueComponentArtifactIdentifierSerializer());
        register(DefaultModuleComponentArtifactIdentifier.class, new ComponentArtifactIdentifierSerializer());
        register(ModuleComponentFileArtifactIdentifier.class, new ModuleComponentFileArtifactIdentifierSerializer());
        register(ComponentFileArtifactIdentifier.class, new ComponentFileArtifactIdentifierSerializer());
        register(TransformedComponentFileArtifactIdentifier.class, new TransformedComponentFileArtifactIdentifierSerializer());
        register(DefaultModuleComponentIdentifier.class, Cast.uncheckedCast(componentIdentifierSerializer));
        register(AttributeContainer.class, attributeContainerSerializer);
        registerWithFactory(ResolvedVariantResult.class, () -> new ResolvedVariantResultSerializer(componentIdentifierSerializer, attributeContainerSerializer));
        register(ComponentSelectionDescriptor.class, new ComponentSelectionDescriptorSerializer(componentSelectionDescriptorFactory));
        ComponentSelectionReasonSerializer componentSelectionReasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        register(ComponentSelectionReason.class, componentSelectionReasonSerializer);
        registerWithFactory(ComponentSelector.class, () -> new ComponentSelectorSerializer(attributeContainerSerializer));
        registerWithFactory(ResolvedComponentResult.class, () -> {
            ResolvedVariantResultSerializer resolvedVariantResultSerializer = new ResolvedVariantResultSerializer(componentIdentifierSerializer, attributeContainerSerializer);
            ComponentSelectorSerializer componentSelectorSerializer = new ComponentSelectorSerializer(attributeContainerSerializer);
            return new ResolvedComponentResultSerializer(moduleVersionIdentifierSerializer, componentIdentifierSerializer, componentSelectorSerializer, resolvedVariantResultSerializer, componentSelectionReasonSerializer);
        });
    }

    @Override
    public boolean canSerialize(Class<?> baseType) {
        return super.canSerialize(baseTypeOf(baseType));
    }

    @Override
    public <T> Serializer<T> build(Class<T> baseType) {
        return super.build(Cast.uncheckedCast(baseTypeOf(baseType)));
    }

    private static Class<?> baseTypeOf(Class<?> type) {
        for (Class<?> supportedType : SUPPORTED_TYPES) {
            if (supportedType.isAssignableFrom(type)) {
                return supportedType;
            }
        }
        return type;
    }

    /**
     * A thread-safe and reusable serializer for {@link OpaqueComponentArtifactIdentifier}.
     */
    private static class OpaqueComponentArtifactIdentifierSerializer implements Serializer<OpaqueComponentArtifactIdentifier> {

        @Override
        public OpaqueComponentArtifactIdentifier read(Decoder decoder) throws Exception {
            return new OpaqueComponentArtifactIdentifier(new File(decoder.readString()));
        }

        @Override
        public void write(Encoder encoder, OpaqueComponentArtifactIdentifier value) throws Exception {
            encoder.writeString(value.getFile().getCanonicalPath());
        }
    }
}
