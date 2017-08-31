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

package org.gradle.workers.internal;

import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.DefaultWorkerDirectoryProvider;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.WorkerExecutor;

public class WorkersServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new GradleUserHomeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeServices());
    }

    private static class BuildSessionScopeServices {

        WorkerDaemonFactory createWorkerDaemonFactory(WorkerDaemonClientsManager workerDaemonClientsManager, MemoryManager memoryManager, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
            return new WorkerDaemonFactory(workerDaemonClientsManager, memoryManager, workerLeaseRegistry, buildOperationExecutor);
        }

        IsolatedClassloaderWorkerFactory createIsolatedClassloaderWorkerFactory(ClassLoaderFactory classLoaderFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
            return new IsolatedClassloaderWorkerFactory(classLoaderFactory, workerLeaseRegistry, buildOperationExecutor);
        }

        WorkerDirectoryProvider createWorkerDirectoryProvider(GradleUserHomeDirProvider gradleUserHomeDirProvider) {
            return new DefaultWorkerDirectoryProvider(gradleUserHomeDirProvider);
        }

    }

    private static class GradleUserHomeServices {
        WorkerDaemonClientsManager createWorkerDaemonClientsManager(WorkerProcessFactory workerFactory,
                                                                    LoggingManagerInternal loggingManager,
                                                                    ListenerManager listenerManager) {
            return new WorkerDaemonClientsManager(new WorkerDaemonStarter(workerFactory, loggingManager), listenerManager, loggingManager);
        }
    }

    private static class ProjectScopeServices {

        WorkerExecutor createWorkerExecutor(InstantiatorFactory instantiatorFactory, WorkerDaemonFactory daemonWorkerFactory, IsolatedClassloaderWorkerFactory isolatedClassloaderWorkerFactory, FileResolver fileResolver, ExecutorFactory executorFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker asyncWorkTracker, WorkerDirectoryProvider workerDirectoryProvider) {
            NoIsolationWorkerFactory noIsolationWorkerFactory = new NoIsolationWorkerFactory(workerLeaseRegistry, buildOperationExecutor, asyncWorkTracker, instantiatorFactory);
            DefaultWorkerExecutor workerExecutor = instantiatorFactory.decorate().newInstance(DefaultWorkerExecutor.class, daemonWorkerFactory, isolatedClassloaderWorkerFactory, noIsolationWorkerFactory, fileResolver, executorFactory, workerLeaseRegistry, buildOperationExecutor, asyncWorkTracker, workerDirectoryProvider);
            noIsolationWorkerFactory.setWorkerExecutor(workerExecutor);
            return workerExecutor;
        }
    }
}
