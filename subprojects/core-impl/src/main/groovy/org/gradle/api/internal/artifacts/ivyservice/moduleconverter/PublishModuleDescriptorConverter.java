/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class PublishModuleDescriptorConverter implements ModuleDescriptorConverter {
    static final String IVY_MAVEN_NAMESPACE = "http://ant.apache.org/ivy/maven";
    static final String IVY_MAVEN_NAMESPACE_PREFIX = "m";

    private ModuleDescriptorConverter resolveModuleDescriptorConverter;
    private ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter;

    public PublishModuleDescriptorConverter(ModuleDescriptorConverter resolveModuleDescriptorConverter,
                                            ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter) {
        this.resolveModuleDescriptorConverter = resolveModuleDescriptorConverter;
        this.artifactsToModuleDescriptorConverter = artifactsToModuleDescriptorConverter;
    }

    public ModuleDescriptor convert(Set<? extends Configuration> configurations, Module module) {
         DefaultModuleDescriptor moduleDescriptor = (DefaultModuleDescriptor) resolveModuleDescriptorConverter
                .convert(configurations, module);
        moduleDescriptor.addExtraAttributeNamespace(IVY_MAVEN_NAMESPACE_PREFIX, IVY_MAVEN_NAMESPACE);
        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, configurations);
        return moduleDescriptor;
    }
}
