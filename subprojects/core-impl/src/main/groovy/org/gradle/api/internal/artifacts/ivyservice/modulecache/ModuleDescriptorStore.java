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
import org.apache.ivy.plugins.parser.ParserSettings;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyContextualiser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleDescriptorParser;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

public class ModuleDescriptorStore {

    public static final String FILE_PATH_PATTERN = "module-metadata/%s/%s/%s/%s/ivy.xml";
    private final ModuleDescriptorParser parser;
    private final PathKeyFileStore pathKeyFileStore;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public ModuleDescriptorStore(PathKeyFileStore pathKeyFileStore, IvyModuleDescriptorWriter ivyModuleDescriptorWriter, ModuleDescriptorParser ivyXmlModuleDescriptorParser) {
        this.pathKeyFileStore = pathKeyFileStore;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
        parser = ivyXmlModuleDescriptorParser;
    }

    public ModuleDescriptor getModuleDescriptor(ModuleVersionRepository repository, ModuleVersionIdentifier moduleVersionIdentifier) {
        String filePath = getFilePath(repository, moduleVersionIdentifier);
        final LocallyAvailableResource resource = pathKeyFileStore.get(filePath);
        if (resource != null) {
            return parseModuleDescriptorFile(resource.getFile());
        }
        return null;
    }

    public LocallyAvailableResource putModuleDescriptor(ModuleVersionRepository repository, final ModuleDescriptor moduleDescriptor) {
        String filePath = getFilePath(repository, moduleDescriptor.getModuleRevisionId());
        return pathKeyFileStore.add(filePath, new Action<File>() {
            public void execute(File moduleDescriptorFile) {
                try {
                    ivyModuleDescriptorWriter.write(moduleDescriptor, moduleDescriptorFile);
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    private ModuleDescriptor parseModuleDescriptorFile(File moduleDescriptorFile) {
        ParserSettings settings = IvyContextualiser.getIvyContext().getSettings();
        try {
            return parser.parseDescriptor(settings, moduleDescriptorFile, false);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String getFilePath(ModuleVersionRepository repository, ModuleRevisionId moduleRevisionId) {
        return String.format(FILE_PATH_PATTERN, moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision(), repository.getId());
    }

    private String getFilePath(ModuleVersionRepository repository, ModuleVersionIdentifier moduleVersionIdentifier) {
        return String.format(FILE_PATH_PATTERN, moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion(), repository.getId());
    }
}