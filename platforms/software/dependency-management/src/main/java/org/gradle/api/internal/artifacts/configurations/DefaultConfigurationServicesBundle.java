/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.model.CalculatedValueContainerFactory;

/**
 * Default implementation of services bundle used by {@link DefaultConfiguration}.
 * <p>
 * This type exists to minimize the number of references that each configuration needs to maintain, thus reducing memory usage
 * and improving performance in large projects with many configurations.
 * <p>
 * Every service, factory, or other type in this bundle <strong>must</strong> be effectively immutable.
 */
public final class DefaultConfigurationServicesBundle implements ConfigurationServicesBundle {
    public final CalculatedValueContainerFactory calculatedValueContainerFactory;
    public final ObjectFactory objectFactory;
    public final FileCollectionFactory fileCollectionFactory;
    public final TaskDependencyFactory taskDependencyFactory;
    public final AttributesFactory attributesFactory;
    public final DomainObjectCollectionFactory domainObjectCollectionFactory;
    public final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    public final InternalProblems problems;

    public DefaultConfigurationServicesBundle(CalculatedValueContainerFactory calculatedValueContainerFactory,
                                              ObjectFactory objectFactory,
                                              FileCollectionFactory fileCollectionFactory,
                                              TaskDependencyFactory taskDependencyFactory,
                                              AttributesFactory attributesFactory,
                                              DomainObjectCollectionFactory domainObjectCollectionFactory,
                                              CollectionCallbackActionDecorator collectionCallbackActionDecorator,
                                              InternalProblems problems) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.objectFactory = objectFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.problems = problems;
    }

    @Override
    public AttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    @Override
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    @Override
    public TaskDependencyFactory getTaskDependencyFactory() {
        return taskDependencyFactory;
    }

    @Override
    public DomainObjectCollectionFactory getDomainObjectCollectionFactory() {
        return domainObjectCollectionFactory;
    }

    @Override
    public CalculatedValueContainerFactory getCalculatedValueContainerFactory() {
        return calculatedValueContainerFactory;
    }

    @Override
    public FileCollectionFactory getFileCollectionFactory() {
        return fileCollectionFactory;
    }

    @Override
    public CollectionCallbackActionDecorator getCollectionCallbackActionDecorator() {
        return collectionCallbackActionDecorator;
    }

    @Override
    public InternalProblems getProblems() {
        return problems;
    }
}
