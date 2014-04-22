/*
 * Copyright 2014 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.util.Collections;
import java.util.Map;

public class ClientModuleVersionMetaData extends ModuleDescriptorAdapter implements IvyModuleVersionMetaData, MavenModuleVersionMetaData {
    public ClientModuleVersionMetaData(ModuleVersionIdentifier id, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentId) {
        super(id, moduleDescriptor, componentId);
    }

    public ClientModuleVersionMetaData(ModuleDescriptor moduleDescriptor) {
        super(moduleDescriptor);
    }

    @Override
    public ModuleDescriptorAdapter copy() {
        ClientModuleVersionMetaData copy = new ClientModuleVersionMetaData(getId(), getDescriptor(), getComponentId());
        copyTo(copy);
        return copy;
    }

    public Map<String, String> getExtraInfo() {
        return Collections.emptyMap();
    }

    public String getPackaging() {
        return "jar";
    }

    public boolean isRelocated() {
        return false;
    }

    public boolean isPomPackaging() {
        return false;
    }

    public boolean isKnownJarPackaging() {
        return true;
    }
}
