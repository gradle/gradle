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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfigurationMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData;

import java.util.List;

public class ModuleDescriptorAdapter implements ModuleVersionMetaData {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleDescriptor moduleDescriptor;

    public ModuleDescriptorAdapter(ModuleRevisionId moduleRevisionId, ModuleDescriptor moduleDescriptor) {
        this.moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
        this.moduleDescriptor = moduleDescriptor;
    }

    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    public ModuleDescriptor getDescriptor() {
        return moduleDescriptor;
    }

    // The methods will require implementing when we stop handing around ModuleDescriptor and make ModuleVersionMetaData our key internal API.
    public List<DependencyMetaData> getDependencies() {
        // TODO:DAZ
        throw new UnsupportedOperationException();
    }

    public ConfigurationMetaData getConfiguration(String name) {
        // TODO:DAZ
        throw new UnsupportedOperationException();
    }

    public boolean isChanging() {
        // TODO:DAZ
        throw new UnsupportedOperationException();
    }
}
