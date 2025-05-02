/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.artifacts.maven.PomModuleDescriptor;
import org.gradle.api.internal.artifacts.DefaultPomModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata;

class MetadataDescriptorFactory {

    private final ModuleComponentResolveMetadata metadata;

    public MetadataDescriptorFactory(ModuleComponentResolveMetadata metadata) {
        this.metadata = metadata;
    }

    public <T> T createDescriptor(Class<T> descriptorClass) {
        if (isIvyMetadata(descriptorClass, metadata)) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metadata;
            IvyModuleDescriptor descriptor = new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
            return descriptorClass.cast(descriptor);
        } else if (isPomMetadata(descriptorClass, metadata)) {
            MavenModuleResolveMetadata mavenMetadata = (MavenModuleResolveMetadata) metadata;
            PomModuleDescriptor descriptor = new DefaultPomModuleDescriptor(mavenMetadata);
            return descriptorClass.cast(descriptor);
        }
        return null;
    }

    public static boolean isMatchingMetadata(Class<?> descriptor, ModuleComponentResolveMetadata metadata) {
        return isPomMetadata(descriptor, metadata) || isIvyMetadata(descriptor, metadata);
    }

    private static boolean isIvyMetadata(Class<?> descriptor, ModuleComponentResolveMetadata metadata) {
        return IvyModuleDescriptor.class.isAssignableFrom(descriptor) && metadata instanceof IvyModuleResolveMetadata;
    }

    private static boolean isPomMetadata(Class<?> descriptor, ModuleComponentResolveMetadata metadata) {
        return PomModuleDescriptor.class.isAssignableFrom(descriptor) && metadata instanceof MavenModuleResolveMetadata;
    }

}
