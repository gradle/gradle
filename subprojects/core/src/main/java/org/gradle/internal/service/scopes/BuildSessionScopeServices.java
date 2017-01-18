/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.cache.DefaultGeneratedGradleJarCache;
import org.gradle.api.internal.cache.GeneratedGradleJarCache;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.caching.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.DefaultBuildCacheConfiguration;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.operations.DefaultBuildOperationProcessor;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.DefaultBuildOperationWorkerRegistry;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider;
import org.gradle.util.GradleVersion;

/**
 * Contains the services for a single build session, which could be a single build or multiple builds when in continuous mode.
 */
public class BuildSessionScopeServices extends DefaultServiceRegistry {

    public BuildSessionScopeServices(final ServiceRegistry parent, final StartParameter startParameter, ClassPath injectedPluginClassPath) {
        super(parent);
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                add(StartParameter.class, startParameter);
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerBuildSessionServices(registration);
                }
            }
        });
        add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        addProvider(new CacheRepositoryServices(startParameter.getGradleUserHomeDir(), startParameter.getProjectCacheDir()));
    }

    DeploymentRegistry createDeploymentRegistry() {
        return new DefaultDeploymentRegistry();
    }

    BuildOperationProcessor createBuildOperationProcessor(BuildOperationWorkerRegistry buildOperationWorkerRegistry, StartParameter startParameter, ExecutorFactory executorFactory) {
        return new DefaultBuildOperationProcessor(new DefaultBuildOperationQueueFactory(buildOperationWorkerRegistry), executorFactory, startParameter.getMaxWorkerCount());
    }

    BuildOperationWorkerRegistry createBuildOperationWorkerRegistry(StartParameter startParameter) {
        return new DefaultBuildOperationWorkerRegistry(startParameter.getMaxWorkerCount());
    }


    WorkerProcessFactory createWorkerProcessFactory(StartParameter startParameter, MessagingServer messagingServer, ClassPathRegistry classPathRegistry,
                                                    TemporaryFileProvider temporaryFileProvider, JavaExecHandleFactory execHandleFactory, JvmVersionDetector jvmVersionDetector,
                                                    MemoryManager memoryManager) {
        return new DefaultWorkerProcessFactory(
            startParameter.getLogLevel(),
            messagingServer,
            classPathRegistry,
            new LongIdGenerator(),
            startParameter.getGradleUserHomeDir(),
            temporaryFileProvider,
            execHandleFactory,
            jvmVersionDetector,
            get(OutputEventListener.class),
            memoryManager
        );
    }

    ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry(
                new DefaultClassPathProvider(get(ModuleRegistry.class)),
                get(WorkerProcessClassPathProvider.class)
        );
    }

    WorkerProcessClassPathProvider createWorkerProcessClassPathProvider(CacheRepository cacheRepository) {
        return new WorkerProcessClassPathProvider(cacheRepository);
    }

    BuildCacheConfigurationInternal createBuildCacheConfiguration(CacheRepository cacheRepository, StartParameter startParameter) {
        return new DefaultBuildCacheConfiguration(cacheRepository, startParameter);
    }

    GeneratedGradleJarCache createGeneratedGradleJarCache(CacheRepository cacheRepository) {
        String gradleVersion = GradleVersion.current().getVersion();
        return new DefaultGeneratedGradleJarCache(cacheRepository, gradleVersion);
    }

    ImmutableAttributesFactory createImmutableAttributesFactory() {
        return new DefaultImmutableAttributesFactory();
    }
}
