/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;

class DefaultComponentMetadataContext implements ComponentMetadataContext {

    private final ComponentMetadataDetails details;
    // We keep this field for access from Groovy scripts, as we currently miss some public API: https://github.com/gradle/gradle/issues/12349
    @SuppressWarnings("UnusedVariable")
    private final ModuleComponentResolveMetadata metadata;
    private final MetadataDescriptorFactory descriptorFactory;

    DefaultComponentMetadataContext(ComponentMetadataDetails details, ModuleComponentResolveMetadata metadata) {
        this.metadata = metadata;
        this.details = details;
        this.descriptorFactory = new MetadataDescriptorFactory(metadata);
    }

    @Override
    public <T> T getDescriptor(Class<T> descriptorClass) {
        return descriptorFactory.createDescriptor(descriptorClass);
    }

    @Override
    public ComponentMetadataDetails getDetails() {
        return details;
    }
}
