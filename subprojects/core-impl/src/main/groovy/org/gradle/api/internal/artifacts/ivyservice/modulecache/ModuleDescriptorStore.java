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
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyContextualiser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.net.URL;

public class ModuleDescriptorStore {

    private final ModuleDescriptorFileStore moduleDescriptorFileStore;
    private final XmlModuleDescriptorParser parser = XmlModuleDescriptorParser.getInstance();

    public ModuleDescriptorStore(ModuleDescriptorFileStore moduleDescriptorFileStore) {
        this.moduleDescriptorFileStore = moduleDescriptorFileStore;
    }

    public ModuleDescriptor getModuleDescriptor(ModuleVersionRepository repository, ModuleRevisionId moduleRevisionId) {
        File moduleDescriptorFile = moduleDescriptorFileStore.getModuleDescriptorFile(repository, moduleRevisionId);
        if (moduleDescriptorFile.exists()) {
            return parseModuleDescriptorFile(moduleDescriptorFile);
        }
        return null;
    }

    private ModuleDescriptor parseModuleDescriptorFile(File moduleDescriptorFile) {
        ParserSettings settings = IvyContextualiser.getIvyContext().getSettings();
        try {
            URL result = moduleDescriptorFile.toURI().toURL();
            return parser.parseDescriptor(settings, result, false);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void putModuleDescriptor(ModuleVersionRepository repository, final ModuleDescriptor moduleDescriptor) {
        moduleDescriptorFileStore.writeModuleDescriptorFile(repository, moduleDescriptor);
    }
}
