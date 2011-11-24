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
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

public class ModuleDescriptorStore {

    private final ModuleDescriptorFileStore moduleDescriptorFileStore;
    private final XmlModuleDescriptorParser parser = XmlModuleDescriptorParser.getInstance();

    public ModuleDescriptorStore(ModuleDescriptorFileStore moduleDescriptorFileStore) {
        this.moduleDescriptorFileStore = moduleDescriptorFileStore;
    }

    public ModuleDescriptor getModuleDescriptor(DependencyResolver dependencyResolver, ModuleRevisionId moduleRevisionId, IvySettings ivySettings) {
        File moduleDescriptorFile = moduleDescriptorFileStore.getModuleDescriptorFile(dependencyResolver, moduleRevisionId);
        return parseModuleDescriptorFile(moduleDescriptorFile, ivySettings);
    }

    private ModuleDescriptor parseModuleDescriptorFile(File moduleDescriptorFile, IvySettings ivySettings)  {
        try {
            URL result = moduleDescriptorFile.toURI().toURL();
            return parser.parseDescriptor(ivySettings, result, false);
        } catch (MalformedURLException e) {
            // TODO:DAZ
            throw new RuntimeException("BAD", e);
        } catch (ParseException e) {
            throw new RuntimeException("BAD", e);
        } catch (IOException e) {
            throw new RuntimeException("BAD", e);
        }
    }

    public void putModuleDescriptor(DependencyResolver dependencyResolver, ModuleDescriptor moduleDescriptor) {
        File moduleDescriptorFile = moduleDescriptorFileStore.getModuleDescriptorFile(dependencyResolver, moduleDescriptor.getModuleRevisionId());
        try {
            // TODO:DAZ use parser/writer directly, so it's a clean write, not an update
            moduleDescriptor.toIvyFile(moduleDescriptorFile);
        } catch (ParseException e) {
            // TODO:DAZ
            throw new RuntimeException("BAD", e);
        } catch (IOException e) {
            throw new RuntimeException("BAD", e);
        }

    }
}
