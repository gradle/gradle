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
package org.gradle.internal.component.external.model;

import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.model.ModuleSource;

import java.util.Map;

public class DefaultIvyModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements IvyModuleResolveMetadata {
    DefaultIvyModuleResolveMetadata(MutableIvyModuleResolveMetadata metadata) {
        super(metadata);
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
    }

    @Override
    public DefaultIvyModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultIvyModuleResolveMetadata(this, source);
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return new DefaultMutableIvyModuleResolveMetadata(this);
    }

    public String getBranch() {
        return getDescriptor().getBranch();
    }

    public Map<NamespaceId, String> getExtraInfo() {
        return getDescriptor().getExtraInfo();
    }
}
