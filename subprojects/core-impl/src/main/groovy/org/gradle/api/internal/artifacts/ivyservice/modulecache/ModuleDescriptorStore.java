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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

public class ModuleDescriptorStore {

    public static final String FILE_PATH_PATTERN = "%s/%s/%s/%s/ivy.xml";
    private final IvyXmlModuleDescriptorParser descriptorParser;
    private final PathKeyFileStore metaDataStore;
    private final IvyModuleDescriptorWriter descriptorWriter;

    public ModuleDescriptorStore(PathKeyFileStore metaDataStore, IvyModuleDescriptorWriter descriptorWriter, IvyXmlModuleDescriptorParser ivyXmlModuleDescriptorParser) {
        this.metaDataStore = metaDataStore;
        this.descriptorWriter = descriptorWriter;
        this.descriptorParser = ivyXmlModuleDescriptorParser;
    }

    public ModuleDescriptor getModuleDescriptor(ModuleComponentRepository repository, ModuleComponentIdentifier moduleComponentIdentifier) {
        String filePath = getFilePath(repository, moduleComponentIdentifier);
        final LocallyAvailableResource resource = metaDataStore.get(filePath);
        if (resource != null) {
            return parseModuleDescriptorFile(resource.getFile());
        }
        return null;
    }

    public LocallyAvailableResource putModuleDescriptor(ModuleComponentRepository repository, final ModuleDescriptor moduleDescriptor) {
        String filePath = getFilePath(repository, moduleDescriptor.getModuleRevisionId());
        return metaDataStore.add(filePath, new Action<File>() {
            public void execute(File moduleDescriptorFile) {
                try {
                    descriptorWriter.write(moduleDescriptor, moduleDescriptorFile);
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    private ModuleDescriptor parseModuleDescriptorFile(File moduleDescriptorFile) {
        DescriptorParseContext parserSettings = new CachedModuleDescriptorParseContext();
        return descriptorParser.parseMetaData(parserSettings, moduleDescriptorFile, false).getDescriptor();
    }

    private String getFilePath(ModuleComponentRepository repository, ModuleRevisionId moduleRevisionId) {
        return String.format(FILE_PATH_PATTERN, moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision(), repository.getId());
    }

    private String getFilePath(ModuleComponentRepository repository, ModuleComponentIdentifier moduleComponentIdentifier) {
        return String.format(FILE_PATH_PATTERN, moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion(), repository.getId());
    }
}