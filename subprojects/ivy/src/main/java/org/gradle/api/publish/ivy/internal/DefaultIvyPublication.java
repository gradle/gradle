/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.reflect.Instantiator;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyDependencyDescriptorInternal ivy;
    private final ConfigurationInternal configuration;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;

    public DefaultIvyPublication(String name, Instantiator instantiator, ConfigurationInternal configuration, DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.name = name;
        this.ivy = instantiator.newInstance(DefaultIvyDependencyDescriptor.class);
        this.configuration = configuration;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public String getName() {
        return name;
    }

    public IvyDependencyDescriptorInternal getIvy() {
        return ivy;
    }

    public FileCollection getPublishableFiles() {
        return configuration.getAllArtifacts().getFiles();
    }

    public TaskDependency getBuildDependencies() {
        return configuration.getAllArtifacts().getBuildDependencies();
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        return new IvyNormalizedPublication(getModule(), configuration, ivy.getDescriptorFile(), ivy.getDescriptorTransformer());
    }

    public Class<IvyNormalizedPublication> getNormalisedPublicationType() {
        return IvyNormalizedPublication.class;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Module getModule() {
        return dependencyMetaDataProvider.getModule();
    }
}
