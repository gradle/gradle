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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.jspecify.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

public class ModuleMetadataStore {

    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final PathKeyFileStore metaDataStore;

    public ModuleMetadataStore(PathKeyFileStore metaDataStore) {
        this.metaDataStore = metaDataStore;
    }

    public byte @Nullable[] getModuleDescriptor(ModuleComponentAtRepositoryKey component) {
        String[] filePath = getFilePath(component);
        LocallyAvailableResource resource = metaDataStore.get(filePath);
        try {
            return Files.readAllBytes(resource.getFile().toPath());
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Could not load module metadata from " + resource.getDisplayName(), e);
        }
    }

    public LocallyAvailableResource putModuleDescriptor(ModuleComponentAtRepositoryKey component, byte[] data) {
        String[] filePath = getFilePath(component);
        return metaDataStore.add(PATH_JOINER.join(filePath), moduleDescriptorFile -> {
            try (FileOutputStream outputStream = new FileOutputStream(moduleDescriptorFile)) {
                outputStream.write(data);
            } catch (IOException e) {
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
