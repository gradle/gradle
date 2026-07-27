/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.model;

import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.ServiceLookup;

/**
 * Default implementation of ObjectFactoryFactory.
 */
public class DefaultObjectFactoryFactory implements ObjectFactoryFactory {
    private final InstantiatorFactory instantiatorFactory;
    private final PatternSetFactory patternSetFactory;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final PropertyFactory propertyFactory;
    private final FilePropertyFactory filePropertyFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final NamedObjectInstantiator namedObjectInstantiator;

    public DefaultObjectFactoryFactory(InstantiatorFactory instantiatorFactory, PatternSetFactory patternSetFactory, DirectoryFileTreeFactory directoryFileTreeFactory, PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory, DomainObjectCollectionFactory domainObjectCollectionFactory, NamedObjectInstantiator namedObjectInstantiator) {
        this.instantiatorFactory = instantiatorFactory;
        this.patternSetFactory = patternSetFactory;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.propertyFactory = propertyFactory;
        this.filePropertyFactory = filePropertyFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.namedObjectInstantiator = namedObjectInstantiator;
    }

    @Override
    public ObjectFactory createObjectFactory(ServiceLookup serviceLookup) {
        return new DefaultObjectFactory(
            instantiatorFactory.decorate(serviceLookup),
            namedObjectInstantiator,
            directoryFileTreeFactory,
            patternSetFactory,
            propertyFactory,
            filePropertyFactory,
            taskDependencyFactory,
            fileCollectionFactory,
            domainObjectCollectionFactory
        );
    }
}
