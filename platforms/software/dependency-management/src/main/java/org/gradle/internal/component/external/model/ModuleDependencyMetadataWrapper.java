/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;

public class ModuleDependencyMetadataWrapper extends DelegatingDependencyMetadata implements ModuleDependencyMetadata {
    private final DependencyMetadata delegate;

    public ModuleDependencyMetadataWrapper(DependencyMetadata delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        ModuleComponentSelector selector = getSelector();
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getRequestedCapabilities());
        return new ModuleDependencyMetadataWrapper(delegate.withTarget(newSelector));
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new ModuleDependencyMetadataWrapper(delegate.withReason(reason));
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        if (delegate instanceof ModuleDependencyMetadata) {
            return new ModuleDependencyMetadataWrapper(((ModuleDependencyMetadata) delegate).withEndorseStrictVersions(endorse));
        }
        return this;
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return (ModuleComponentSelector) delegate.getSelector();
    }
}
