/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.DefaultArchiveOperations;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.DefaultFileSystemOperations;
import org.gradle.api.internal.file.DefaultProjectLayout;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.archive.DecompressionCoordinator;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.DefaultExecOperations;
import org.gradle.process.internal.ExecFactory;

import java.io.File;

/**
 * These Project scoped services are shared between the main build process and worker processes.
 */
public class WorkerSharedProjectScopeServices implements ServiceRegistrationProvider {
    private final File projectDir;

    public WorkerSharedProjectScopeServices(File projectDir) {
        this.projectDir = projectDir;
    }

    void configure(ServiceRegistration registration) {
        registration.add(DefaultPropertyFactory.class);
        registration.add(DefaultFilePropertyFactory.class);
    }

    @Provides
    protected FileCollectionFactory createFileCollectionFactory(FileCollectionFactory parent, PathToFileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost) {
        return parent.forChildScope(fileResolver, taskDependencyFactory, propertyHost);
    }

    @Provides
    protected FileResolver createFileResolver(FileLookup lookup) {
        return lookup.getFileResolver(projectDir);
    }

    @Provides
    protected DefaultFileOperations createFileOperations(
            FileResolver fileResolver,
            Instantiator instantiator,
            DirectoryFileTreeFactory directoryFileTreeFactory,
            FileHasher fileHasher,
            DefaultResourceHandler.Factory resourceHandlerFactory,
            FileCollectionFactory fileCollectionFactory,
            ObjectFactory objectFactory,
            FileSystem fileSystem,
            Factory<PatternSet> patternSetFactory,
            Deleter deleter,
            DocumentationRegistry documentationRegistry,
            ProviderFactory providers,
            TaskDependencyFactory taskDependencyFactory,
            DecompressionCoordinator decompressionCoordinator,
            TemporaryFileProvider temporaryFileProvider
    ) {
        return new DefaultFileOperations(
                fileResolver,
                instantiator,
                directoryFileTreeFactory,
                fileHasher,
                resourceHandlerFactory,
                fileCollectionFactory,
                objectFactory,
                fileSystem,
                patternSetFactory,
                deleter,
                documentationRegistry,
                taskDependencyFactory,
                providers,
            decompressionCoordinator,
                temporaryFileProvider
        );
    }

    @Provides
    protected FileSystemOperations createFileSystemOperations(ObjectFactory objectFactory, Instantiator instantiator, FileOperations fileOperations) {
        return instantiator.newInstance(DefaultFileSystemOperations.class, objectFactory, fileOperations);
    }

    @Provides
    protected ArchiveOperations createArchiveOperations(Instantiator instantiator, FileOperations fileOperations) {
        return instantiator.newInstance(DefaultArchiveOperations.class, fileOperations);
    }

    @Provides
    protected ExecOperations createExecOperations(Instantiator instantiator, ExecFactory execFactory) {
        return instantiator.newInstance(DefaultExecOperations.class, execFactory);
    }

    @Provides
    ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services, Factory<PatternSet> patternSetFactory, DirectoryFileTreeFactory directoryFileTreeFactory,
                                      PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory,
                                      DomainObjectCollectionFactory domainObjectCollectionFactory, NamedObjectInstantiator namedObjectInstantiator) {
        return new DefaultObjectFactory(
                instantiatorFactory.decorate(services),
                namedObjectInstantiator,
                directoryFileTreeFactory,
                patternSetFactory,
                propertyFactory,
                filePropertyFactory,
                taskDependencyFactory,
                fileCollectionFactory,
                domainObjectCollectionFactory);
    }

    @Provides
    DefaultProjectLayout createProjectLayout(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, TaskDependencyFactory taskDependencyFactory,
                                             FilePropertyFactory filePropertyFactory, Factory<PatternSet> patternSetFactory, PropertyHost propertyHost, FileFactory fileFactory) {
        return new DefaultProjectLayout(projectDir, fileResolver, taskDependencyFactory, patternSetFactory, propertyHost, fileCollectionFactory, filePropertyFactory, fileFactory);
    }
}
