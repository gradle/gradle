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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

public class ModuleDescriptorAdapter extends AbstractModuleDescriptorBackedMetaData implements MutableModuleVersionMetaData {

    public static ModuleDescriptorAdapter defaultForDependency(DependencyMetaData dependencyMetaData) {
        DependencyDescriptor dependencyDescriptor = dependencyMetaData.getDescriptor();
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(dependencyDescriptor.getDependencyRevisionId(), dependencyDescriptor.getAllDependencyArtifacts());
        moduleDescriptor.setStatus("integration");
        return new ModuleDescriptorAdapter(moduleDescriptor);
    }

    public ModuleDescriptorAdapter(ModuleDescriptor moduleDescriptor) {
        this(DefaultModuleVersionIdentifier.newId(moduleDescriptor.getModuleRevisionId()), moduleDescriptor);
    }

    public ModuleDescriptorAdapter(ModuleVersionIdentifier identifier, ModuleDescriptor moduleDescriptor) {
        this(identifier, moduleDescriptor, DefaultModuleComponentIdentifier.newId(identifier));
    }

    public ModuleDescriptorAdapter(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ComponentIdentifier componentIdentifier) {
        super(moduleVersionIdentifier, moduleDescriptor, componentIdentifier);
    }

    public ModuleDescriptorAdapter copy() {
        // TODO:ADAM - need to make a copy of the descriptor (it's effectively immutable at this point so it's not a problem yet)
        ModuleDescriptorAdapter copy = new ModuleDescriptorAdapter(getId(), getDescriptor(), getComponentId());
        copyTo(copy);
        return copy;
    }

    public ModuleVersionMetaData withSource(ModuleSource source) {
        ModuleDescriptorAdapter copy = copy();
        copy.setModuleSource(source);
        return copy;
    }
}
