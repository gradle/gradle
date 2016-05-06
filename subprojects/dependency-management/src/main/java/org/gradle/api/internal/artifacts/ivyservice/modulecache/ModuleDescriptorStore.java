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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ModuleDescriptorStore {

    private final PathKeyFileStore metaDataStore;
    private final ModuleDescriptorSerializer moduleDescriptorSerializer;

    public ModuleDescriptorStore(PathKeyFileStore metaDataStore, ModuleDescriptorSerializer moduleDescriptorSerializer) {
        this.metaDataStore = metaDataStore;
        this.moduleDescriptorSerializer = moduleDescriptorSerializer;
    }

    public ModuleDescriptorState getModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier moduleComponentIdentifier) {
        String filePath = getFilePath(repository, moduleComponentIdentifier);
        final LocallyAvailableResource resource = metaDataStore.get(filePath);
        if (resource != null) {
            try {
                KryoBackedDecoder decoder = new KryoBackedDecoder(new FileInputStream(resource.getFile()));
                try {
                    return moduleDescriptorSerializer.read(decoder);
                } finally {
                    decoder.close();
                }
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return null;
    }

    public LocallyAvailableResource putModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier moduleComponentIdentifier, final ModuleDescriptorState moduleDescriptor) {
        String filePath = getFilePath(repository, moduleComponentIdentifier);
        return metaDataStore.add(filePath, new Action<File>() {
            public void execute(File moduleDescriptorFile) {
                try {
                    KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(moduleDescriptorFile));
                    moduleDescriptorSerializer.write(encoder, moduleDescriptor);
                    encoder.close();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    private String getFilePath(ModuleComponentRepository repository, ModuleComponentIdentifier moduleComponentIdentifier) {
        return moduleComponentIdentifier.getGroup() + "/" + moduleComponentIdentifier.getModule() + "/" + moduleComponentIdentifier.getVersion() + "/" + repository.getId() + "/descriptor.bin";
    }

}
