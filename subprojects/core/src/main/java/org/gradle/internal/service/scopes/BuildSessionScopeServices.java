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
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider;

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

    WorkerProcessFactory createWorkerProcessFactory(StartParameter startParameter, MessagingServer messagingServer, ClassPathRegistry classPathRegistry,
                                                    TemporaryFileProvider temporaryFileProvider, JavaExecHandleFactory execHandleFactory) {
        return new DefaultWorkerProcessFactory(
            startParameter.getLogLevel(),
            messagingServer,
            classPathRegistry,
            new LongIdGenerator(),
            startParameter.getGradleUserHomeDir(),
            temporaryFileProvider,
            execHandleFactory);
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
}
