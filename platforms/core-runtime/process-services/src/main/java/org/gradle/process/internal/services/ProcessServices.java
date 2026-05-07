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

package org.gradle.process.internal.services;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.jspecify.annotations.NullMarked;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.sources.process.ExecSpecFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.DefaultExecOperations;
import org.gradle.process.internal.DefaultExecSpecFactory;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecFactory;
import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;

@NullMarked
public class ProcessServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalProcessServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new GradleUserHomeProcessServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionProcessServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildProcessServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectProcessServices());
    }

    private static class GlobalProcessServices implements ServiceRegistrationProvider {
        @Provides
        ExecFactory createExecFactory(
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ExecutorFactory executorFactory,
            TemporaryFileProvider temporaryFileProvider,
            BuildCancellationToken buildCancellationToken
        ) {
            return DefaultExecActionFactory.of(
                fileResolver,
                fileCollectionFactory,
                instantiator,
                executorFactory,
                temporaryFileProvider,
                buildCancellationToken,
                objectFactory
            );
        }
    }

    private static class GradleUserHomeProcessServices implements ServiceRegistrationProvider {
        @Provides
        ExecFactory createExecFactory(
            ExecFactory parent,
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            JavaModuleDetector javaModuleDetector
        ) {
            return parent.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .build();
        }
    }

    private static class BuildSessionProcessServices implements ServiceRegistrationProvider {
        @Provides
        ExecFactory decorateExecFactory(
            ExecFactory execFactory,
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            Instantiator instantiator,
            BuildCancellationToken buildCancellationToken,
            ObjectFactory objectFactory,
            JavaModuleDetector javaModuleDetector
        ) {
            return execFactory.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withBuildCancellationToken(buildCancellationToken)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .build();
        }
    }

    private static class BuildProcessServices implements ServiceRegistrationProvider {
        @Provides
        void configure(ServiceRegistration registration) {
            registration.add(ExecOperations.class, DefaultExecOperations.class);
        }

        @Provides
        ExecFactory decorateExecFactory(
            ExecFactory parent,
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            JavaModuleDetector javaModuleDetector,
            ListenerManager listenerManager
        ) {
            return parent.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiator)
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .withExternalProcessStartedListener(listenerManager.getBroadcaster(ExternalProcessStartedListener.class))
                .build();
        }

        @Provides
        ExecSpecFactory createExecSpecFactory(ExecActionFactory execActionFactory) {
            return new DefaultExecSpecFactory(execActionFactory);
        }
    }

    private static class ProjectProcessServices implements ServiceRegistrationProvider {
        @Provides
        ExecFactory decorateExecFactory(
            ExecFactory execFactory,
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            InstantiatorFactory instantiatorFactory,
            ObjectFactory objectFactory,
            JavaModuleDetector javaModuleDetector,
            ListenerManager listenerManager
        ) {
            return execFactory.forContext()
                .withFileResolver(fileResolver)
                .withFileCollectionFactory(fileCollectionFactory)
                .withInstantiator(instantiatorFactory.decorateLenient())
                .withObjectFactory(objectFactory)
                .withJavaModuleDetector(javaModuleDetector)
                .withExternalProcessStartedListener(listenerManager.getBroadcaster(ExternalProcessStartedListener.class))
                .build();
        }
    }
}
