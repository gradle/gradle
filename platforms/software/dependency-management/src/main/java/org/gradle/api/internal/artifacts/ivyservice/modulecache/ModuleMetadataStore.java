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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.base.Joiner;
import com.google.common.collect.Interner;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;

public class ModuleMetadataStore {

    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final PathKeyFileStore metaDataStore;
    private final ModuleMetadataSerializer moduleMetadataSerializer;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final Interner<String> stringInterner;

    public ModuleMetadataStore(PathKeyFileStore metaDataStore,
                               ModuleMetadataSerializer moduleMetadataSerializer,
                               ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                               Interner<String> stringInterner) {
        this.metaDataStore = metaDataStore;
        this.moduleMetadataSerializer = moduleMetadataSerializer;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.stringInterner = stringInterner;
    }

    public MutableModuleComponentResolveMetadata getModuleDescriptor(ModuleComponentAtRepositoryKey component) {
        String[] filePath = getFilePath(component);
        LocallyAvailableResource resource = metaDataStore.get(filePath);
        if (resource != null) {
            try {
                try (StringDeduplicatingDecoder decoder = new StringDeduplicatingDecoder(new KryoBackedDecoder(new FileInputStream(resource.getFile())), stringInterner)) {
                    return moduleMetadataSerializer.read(decoder, moduleIdentifierFactory, new HashMap<>());
                }
            } catch (Exception e) {
                throw new RuntimeException("Could not load module metadata from " + resource.getDisplayName(), e);
            }
        }
        return null;
    }

    public LocallyAvailableResource putModuleDescriptor(ModuleComponentAtRepositoryKey component, final ModuleComponentResolveMetadata metadata) {
        String[] filePath = getFilePath(component);
        return metaDataStore.add(PATH_JOINER.join(filePath), moduleDescriptorFile -> {
            try {
                try (KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(moduleDescriptorFile))) {
                    moduleMetadataSerializer.write(encoder, metadata, new HashMap<>());
                }
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    private String[] getFilePath(ModuleComponentAtRepositoryKey componentId) {
        ModuleComponentIdentifier moduleComponentIdentifier = componentId.getComponentId();
        return new String[] {
            moduleComponentIdentifier.getGroup(),
            moduleComponentIdentifier.getModule(),
            moduleComponentIdentifier.getVersion(),
            componentId.getRepositoryId(),
            "descriptor.bin"
        };
    }

}
