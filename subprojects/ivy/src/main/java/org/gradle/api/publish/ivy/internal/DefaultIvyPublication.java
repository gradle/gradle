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

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.reflect.Instantiator;

import java.util.HashSet;
import java.util.Set;

import static org.gradle.util.CollectionUtils.*;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyModuleDescriptorInternal descriptor;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final Set<? extends Configuration> configurations;
    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;

    public DefaultIvyPublication(
            String name, Instantiator instantiator, Set<? extends Configuration> configurations,
            DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver, TaskResolver taskResolver
    ) {
        this.name = name;
        this.descriptor = instantiator.newInstance(DefaultIvyModuleDescriptor.class);
        this.configurations = configurations;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
    }

    public String getName() {
        return name;
    }

    public IvyModuleDescriptorInternal getDescriptor() {
        return descriptor;
    }

    public void descriptor(Action<? super IvyModuleDescriptor> configure) {
        configure.execute(descriptor);
    }

    public FileCollection getPublishableFiles() {
        return new DefaultConfigurableFileCollection(
                "publication artifacts", fileResolver, taskResolver,
                collect(configurations, new Transformer<FileCollection, Configuration>() {
                    public FileCollection transform(Configuration configuration) {
                        return configuration.getAllArtifacts().getFiles();
                    }
                }));
    }

    public TaskDependency getBuildDependencies() {
        return getPublishableFiles().getBuildDependencies();
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        return new IvyNormalizedPublication(dependencyMetaDataProvider.getModule(), getFlattenedConfigurations(), descriptor.getFile(), descriptor.getTransformer());
    }

    public Class<IvyNormalizedPublication> getNormalisedPublicationType() {
        return IvyNormalizedPublication.class;
    }

    // Flattens each of the given configurations to include any parents, visible or not.
    private Set<Configuration> getFlattenedConfigurations() {
        return inject(new HashSet<Configuration>(), configurations, new Action<InjectionStep<Set<Configuration>, Configuration>>() {
            public void execute(InjectionStep<Set<Configuration>, Configuration> step) {
                step.getTarget().addAll(step.getItem().getHierarchy());
            }
        });
    }

}
