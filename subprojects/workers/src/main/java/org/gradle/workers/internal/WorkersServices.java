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

import org.gradle.StartParameter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.workers.WorkerExecutor;

public class WorkersServices implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildSessionScopeServices {
        WorkerDaemonClientsManager createWorkerDaemonClientsManager(WorkerProcessFactory workerFactory,
                                                                    StartParameter startParameter,
                                                                    BuildOperationExecutor buildOperationExecutor) {
            return new WorkerDaemonClientsManager(new WorkerDaemonStarter(workerFactory, startParameter, buildOperationExecutor));
        }

        WorkerDaemonFactory createWorkerDaemonFactory(WorkerDaemonClientsManager workerDaemonClientsManager, MemoryManager memoryManager, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
            return new WorkerDaemonFactory(workerDaemonClientsManager, memoryManager, workerLeaseRegistry, buildOperationExecutor);
        }

        WorkerExecutor createWorkerExecutor(Instantiator instantiator, WorkerDaemonFactory daemonWorkerFactory, IsolatedClassloaderWorkerFactory isolatedClassloaderWorkerFactory, NoIsolationWorkerFactory noIsolationWorkerFactory, FileResolver fileResolver, ExecutorFactory executorFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker asyncWorkTracker) {
            return instantiator.newInstance(DefaultWorkerExecutor.class, daemonWorkerFactory, isolatedClassloaderWorkerFactory, noIsolationWorkerFactory, fileResolver, executorFactory, workerLeaseRegistry, buildOperationExecutor, asyncWorkTracker);
        }

        IsolatedClassloaderWorkerFactory createIsolatedClassloaderWorkerFactory(ClassLoaderFactory classLoaderFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
            return new IsolatedClassloaderWorkerFactory(classLoaderFactory, workerLeaseRegistry, buildOperationExecutor);
        }

        NoIsolationWorkerFactory createNoIsolationWorkerFactory(WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
            return new NoIsolationWorkerFactory(workerLeaseRegistry, buildOperationExecutor);
        }
    }
}
