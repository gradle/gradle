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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.internal.artifacts.metadata.MutableLocalComponentMetaData;

import java.util.Set;

public class PublishLocalComponentFactory implements LocalComponentFactory {
    static final String IVY_MAVEN_NAMESPACE = "http://ant.apache.org/ivy/maven";
    static final String IVY_MAVEN_NAMESPACE_PREFIX = "m";

    private LocalComponentFactory resolveLocalComponentFactory;
    private ConfigurationsToArtifactsConverter configurationsToArtifactsConverter;

    public PublishLocalComponentFactory(LocalComponentFactory resolveLocalComponentFactory,
                                        ConfigurationsToArtifactsConverter configurationsToArtifactsConverter) {
        this.resolveLocalComponentFactory = resolveLocalComponentFactory;
        this.configurationsToArtifactsConverter = configurationsToArtifactsConverter;
    }

    public MutableLocalComponentMetaData convert(Set<? extends Configuration> configurations, ModuleInternal module) {
        MutableLocalComponentMetaData publishMetaData = resolveLocalComponentFactory.convert(configurations, module);
        DefaultModuleDescriptor moduleDescriptor = publishMetaData.getModuleDescriptor();
        moduleDescriptor.addExtraAttributeNamespace(IVY_MAVEN_NAMESPACE_PREFIX, IVY_MAVEN_NAMESPACE);
        configurationsToArtifactsConverter.addArtifacts(publishMetaData, configurations);
        return publishMetaData;
    }
}
