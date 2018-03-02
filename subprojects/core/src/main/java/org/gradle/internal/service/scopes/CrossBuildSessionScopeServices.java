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

import org.gradle.StartParameter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.trace.BuildOperationTrace;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.progress.DefaultBuildOperationListenerManager;
import org.gradle.internal.progress.DelegatingBuildOperationExecutor;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.DefaultWorkerLeaseService;
import org.gradle.internal.work.StopShieldingWorkerLeaseService;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.Closeable;
import java.io.IOException;

/**
 * Services to be shared across build sessions.
 *
 * Each invocation of a GradleBuild task is a new build session,
 * because it might use a different Gradle user home a session needs a Gradle user home.
 *
 * This mixin gets applied to each build session, sharing its state across them.
 *
 * If we remove the GradleBuild task, or even just the ability for it to use a different Gradle user home …
 * https://github.com/gradle/gradle/issues/4559 …
 * Then we can remove these “tricks”, move the services provided by this to session scope,
 * and use a new build tree scope for each GradleBuild invocation instead of session, and reuse the session across them.
 */
public class CrossBuildSessionScopeServices implements Closeable {

    private final Services services;

    @Override
    public void close() throws IOException {
        services.close();
    }

    // Parent is expected to be the global services
    public CrossBuildSessionScopeServices(ServiceRegistry parent, StartParameter startParameter) {
        this.services = new Services(parent, startParameter);
    }

    GradleLauncherFactory createGradleLauncherFactory() {
        return get(GradleLauncherFactory.class);
    }

    WorkerLeaseService createWorkerLeaseService() {
        return new StopShieldingWorkerLeaseService(get(WorkerLeaseService.class));
    }

    ResourceLockCoordinationService createResourceLockCoordinationService() {
        return get(ResourceLockCoordinationService.class);
    }

    BuildOperationListenerManager createBuildOperationListenerManager() {
        return get(BuildOperationListenerManager.class);
    }

    BuildOperationExecutor createBuildOperationExecutor() {
        // The actual instance is DefaultBuildOperationExecutor which is stoppable.
        // However, we don't want consumers of this method to own the lifecycle.
        // So, we wrap it in a type that doesn't expose the Stoppable aspect.
        return new DelegatingBuildOperationExecutor(get(BuildOperationExecutor.class));
    }

    private <T> T get(Class<T> serviceType) {
        return services.get(serviceType);
    }

    private class Services extends DefaultServiceRegistry {

        private final StartParameter startParameter;
        private final ServiceRegistry parent;

        private Services(ServiceRegistry parent, StartParameter startParameter) {
            super(parent);
            this.parent = parent;
            this.startParameter = startParameter;
            get(BuildOperationTrace.class); // initialize this
        }

        WorkerLeaseService createWorkerLeaseService(ResourceLockCoordinationService coordinationService, ParallelismConfigurationManager parallelismConfigurationManager) {
            return new DefaultWorkerLeaseService(coordinationService, parallelismConfigurationManager);
        }

        GradleLauncherFactory createGradleLauncherFactory(ProgressLoggerFactory progressLoggerFactory, GradleUserHomeScopeServiceRegistry userHomeScopeServiceRegistry) {
            return new DefaultGradleLauncherFactory(parent.get(ListenerManager.class), progressLoggerFactory, userHomeScopeServiceRegistry, CrossBuildSessionScopeServices.this);
        }

        ListenerManager createListenerManager(ListenerManager parent) {
            return parent.createChild();
        }

        ResourceLockCoordinationService createWorkerLeaseCoordinationService() {
            return new DefaultResourceLockCoordinationService();
        }

        BuildOperationListenerManager createBuildOperationListenerManager(ListenerManager listenerManager) {
            return new DefaultBuildOperationListenerManager(listenerManager);
        }

        BuildOperationTrace createBuildOperationTrace() {
            return new BuildOperationTrace(startParameter, parent.get(ListenerManager.class));
        }

        BuildOperationExecutor createBuildOperationExecutor(
            BuildOperationListenerManager buildOperationListenerManager,
            Clock clock,
            ProgressLoggerFactory progressLoggerFactory,
            WorkerLeaseService workerLeaseService,
            ExecutorFactory executorFactory,
            ResourceLockCoordinationService resourceLockCoordinationService,
            ParallelismConfigurationManager parallelismConfigurationManager,
            BuildOperationIdFactory buildOperationIdFactory
        ) {
            return new DefaultBuildOperationExecutor(
                buildOperationListenerManager.getBroadcaster(),
                clock, progressLoggerFactory,
                new DefaultBuildOperationQueueFactory(workerLeaseService),
                executorFactory,
                resourceLockCoordinationService,
                parallelismConfigurationManager,
                buildOperationIdFactory
            );
        }

    }
}
