/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.cache.internal.locklistener.InetAddressProvider;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.event.ScopedListenerManager;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.process.internal.DefaultClientExecHandleBuilderFactory;

import java.net.InetAddress;

/**
 * Defines the basic global services of a given process. This includes the Gradle CLI, daemon and tooling API provider. These services
 * should be as few as possible to keep the CLI startup fast. Global services that are only needed for the process running the build should go in
 * {@link GlobalScopeServices}.
 */
public class BasicGlobalScopeServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration serviceRegistration) {
        serviceRegistration.add(FileLookup.class, DefaultFileLookup.class);
        serviceRegistration.addProvider(new MessagingServices());
    }

    @Provides
    FileLockManager createFileLockManager(ProcessEnvironment processEnvironment, FileLockContentionHandler fileLockContentionHandler) {
        return new DefaultFileLockManager(
            new DefaultProcessMetaDataProvider(
                processEnvironment),
            fileLockContentionHandler);
    }

    @Provides
    FileLockContentionHandler createFileLockContentionHandler(ExecutorFactory executorFactory, InetAddressFactory inetAddressFactory) {
        return new DefaultFileLockContentionHandler(
            executorFactory,
            new InetAddressProvider() {
                @Override
                public InetAddress getWildcardBindingAddress() {
                    return inetAddressFactory.getWildcardBindingAddress();
                }

                @Override
                public Iterable<InetAddress> getCommunicationAddresses() {
                    return inetAddressFactory.getCommunicationAddresses();
                }
            });
    }

    @Provides
    ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    @Provides
    DocumentationRegistry createDocumentationRegistry() {
        return new DocumentationRegistry();
    }

    @Provides
    BuildCancellationToken createBuildCancellationToken() {
        return new DefaultBuildCancellationToken();
    }

    @Provides
    ClientExecHandleBuilderFactory createExecHandleFactory(
        FileResolver fileResolver,
        ExecutorFactory executorFactory,
        BuildCancellationToken buildCancellationToken
    ) {
        return DefaultClientExecHandleBuilderFactory.of(fileResolver, executorFactory, buildCancellationToken);
    }

    @Provides
    FileResolver createFileResolver(FileLookup lookup) {
        return lookup.getFileResolver();
    }

    @Provides
    DirectoryFileTreeFactory createDirectoryFileTreeFactory(Factory<PatternSet> patternSetFactory, FileSystem fileSystem) {
        return new DefaultDirectoryFileTreeFactory(patternSetFactory, fileSystem);
    }

    @Provides
    PropertyHost createPropertyHost() {
        return PropertyHost.NO_OP;
    }

    @Provides
    FileCollectionFactory createFileCollectionFactory(PathToFileResolver fileResolver, Factory<PatternSet> patternSetFactory, DirectoryFileTreeFactory directoryFileTreeFactory, PropertyHost propertyHost, FileSystem fileSystem) {
        return new DefaultFileCollectionFactory(fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem);
    }

    @Provides
    PatternSpecFactory createPatternSpecFactory(ListenerManager listenerManager) {
        PatternSpecFactory patternSpecFactory = PatternSpecFactory.INSTANCE;
        listenerManager.addListener(patternSpecFactory);
        return patternSpecFactory;
    }

    @Provides
    Factory<PatternSet> createPatternSetFactory(final PatternSpecFactory patternSpecFactory) {
        return PatternSets.getPatternSetFactory(patternSpecFactory);
    }

    @Provides
    ScopedListenerManager createListenerManager() {
        return new DefaultListenerManager(Global.class);
    }
}

