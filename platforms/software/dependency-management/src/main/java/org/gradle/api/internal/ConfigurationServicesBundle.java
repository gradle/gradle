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

package org.gradle.api.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A bundle of services used by {@link Configuration}.
 * <p>
 * This type exists to minimize the number of references that each configuration needs to maintain, thus reducing memory usage
 * and improving performance in large projects with many configurations.
 * <p>
 * Every service, factory, or other type in this bundle <strong>must</strong> be effectively immutable.
 */
@ServiceScope(Scope.Project.class)
public interface ConfigurationServicesBundle {
    BuildOperationRunner getBuildOperationRunner();
    ProjectStateRegistry getProjectStateRegistry();
    AttributesFactory getAttributesFactory();
    ObjectFactory getObjectFactory();
    TaskDependencyFactory getTaskDependencyFactory();
    DomainObjectCollectionFactory getDomainObjectCollectionFactory();
    CalculatedValueContainerFactory getCalculatedValueContainerFactory();
    FileCollectionFactory getFileCollectionFactory();
    CollectionCallbackActionDecorator getCollectionCallbackActionDecorator();
    InternalProblems getProblems();
    AttributeDesugaring getAttributeDesugaring();
}
