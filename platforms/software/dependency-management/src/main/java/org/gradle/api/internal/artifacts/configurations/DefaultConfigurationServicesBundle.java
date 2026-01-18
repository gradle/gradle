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
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationRunner;

/**
 * Default implementation of services bundle used by {@link DefaultConfiguration}.
 * <p>
 * This type exists to minimize the number of references that each configuration needs to maintain, thus reducing memory usage
 * and improving performance in large projects with many configurations.
 * <p>
 * Every service, factory, or other type in this bundle <strong>must</strong> be effectively immutable.
 */
public final class DefaultConfigurationServicesBundle implements ConfigurationServicesBundle {
    private final BuildOperationRunner buildOperationRunner;
    private final ProjectStateRegistry projectStateRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ObjectFactory objectFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final AttributesFactory attributesFactory;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    private final InternalProblems problems;
    private final AttributeDesugaring attributeDesugaring;
    private final ResolveExceptionMapper exceptionMapper;
    private final ProviderFactory providerFactory;

    public DefaultConfigurationServicesBundle(BuildOperationRunner buildOperationRunner,
                                              ProjectStateRegistry projectStateRegistry,
                                              CalculatedValueContainerFactory calculatedValueContainerFactory,
                                              ObjectFactory objectFactory,
                                              FileCollectionFactory fileCollectionFactory,
                                              TaskDependencyFactory taskDependencyFactory,
                                              AttributesFactory attributesFactory,
                                              DomainObjectCollectionFactory domainObjectCollectionFactory,
                                              CollectionCallbackActionDecorator collectionCallbackActionDecorator,
                                              InternalProblems problems,
                                              AttributeDesugaring attributeDesugaring,
                                              ResolveExceptionMapper exceptionMapper,
                                              ProviderFactory providerFactory) {
        this.buildOperationRunner = buildOperationRunner;
        this.projectStateRegistry = projectStateRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.objectFactory = objectFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.problems = problems;
        this.attributeDesugaring = attributeDesugaring;
        this.exceptionMapper = exceptionMapper;
        this.providerFactory = providerFactory;
    }

    @Override
    public BuildOperationRunner getBuildOperationRunner() {
        return buildOperationRunner;
    }

    @Override
    public ProjectStateRegistry getProjectStateRegistry() {
        return projectStateRegistry;
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

    @Override
    public AttributeDesugaring getAttributeDesugaring() {
        return attributeDesugaring;
    }

    @Override
    public ResolveExceptionMapper getExceptionMapper() {
        return exceptionMapper;
    }

    @Override
    public ProviderFactory getProviderFactory() {
        return providerFactory;
    }
}
