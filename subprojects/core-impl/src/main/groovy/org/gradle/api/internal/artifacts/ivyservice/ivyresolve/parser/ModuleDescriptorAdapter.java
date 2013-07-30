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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.MutableModuleVersionMetaData;

import java.util.Arrays;
import java.util.List;

public class ModuleDescriptorAdapter implements MutableModuleVersionMetaData {
    private static final List<String> DEFAULT_STATUS_SCHEME = Arrays.asList("integration", "milestone", "release");

    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleDescriptor moduleDescriptor;
    private boolean changing;
    private String status;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;

    public ModuleDescriptorAdapter(ModuleRevisionId moduleRevisionId, ModuleDescriptor moduleDescriptor) {
        this.moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
        this.moduleDescriptor = moduleDescriptor;
        status = moduleDescriptor.getStatus();
    }

    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    public ModuleDescriptor getDescriptor() {
        return moduleDescriptor;
    }

    public boolean isChanging() {
        return changing;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStatusScheme() {
        return statusScheme;
    }

    public void setChanging(boolean changing) {
        this.changing = changing;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStatusScheme(List<String> statusScheme) {
        this.statusScheme = statusScheme;
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
}
