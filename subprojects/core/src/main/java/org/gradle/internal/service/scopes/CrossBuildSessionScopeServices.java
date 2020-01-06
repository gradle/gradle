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
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultCollectionCallbackActionDecorator;
import org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator;
import org.gradle.configuration.internal.DefaultUserCodeApplicationContext;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.DefaultBuildOperationExecutor;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.DelegatingBuildOperationExecutor;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationBridge;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.operations.trace.BuildOperationTrace;
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
 * Generally, one regular Gradle invocation is conceptually a session.
 * However, the GradleBuild task is currently implemented in such a way that it uses a discrete session.
 * Having the GradleBuild task reuse the outer session is complicated because it may use a different Gradle user home.
 * See https://github.com/gradle/gradle/issues/4559.
 *
 * This set of services is effectively a mixin, that gets applied to each build session scope services.
 * It, importantly, is not the parent of build session scope services.
 */
public class CrossBuildSessionScopeServices implements Closeable {
    private final BuildOperationTrace buildOperationTrace;
    private final BuildOperationNotificationBridge buildOperationNotificationBridge;
    private final LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster;
    private final BuildOperationListenerManager buildOperationListenerManager;

    private final Services services;

    public CrossBuildSessionScopeServices(ServiceRegistry parent, StartParameter startParameter) {
        this.services = new Services(parent);

        this.buildOperationListenerManager = parent.get(BuildOperationListenerManager.class);

        ListenerManager generalListenerManager = parent.get(ListenerManager.class);
        this.buildOperationTrace = new BuildOperationTrace(startParameter, buildOperationListenerManager);
        this.buildOperationNotificationBridge = new BuildOperationNotificationBridge(buildOperationListenerManager, generalListenerManager);
        this.loggingBuildOperationProgressBroadcaster = new LoggingBuildOperationProgressBroadcaster(parent.get(OutputEventListenerManager.class), buildOperationListenerManager.getBroadcaster());
    }

    GradleLauncherFactory createGradleLauncherFactory() {
        return services.get(GradleLauncherFactory.class);
    }

    WorkerLeaseService createWorkerLeaseService() {
        return new StopShieldingWorkerLeaseService(services.get(WorkerLeaseService.class));
    }

    BuildOperationListenerManager createBuildOperationListenerManager() {
        return buildOperationListenerManager;
    }

    LoggingBuildOperationProgressBroadcaster createLoggingBuildOperationProgressBroadcaster() {
        return loggingBuildOperationProgressBroadcaster;
    }

    BuildOperationExecutor createBuildOperationExecutor() {
        // Wrap to prevent exposing Stoppable, as we don't want to stop at this scope
        return new DelegatingBuildOperationExecutor(services.get(BuildOperationExecutor.class));
    }

    ListenerBuildOperationDecorator createListenerBuildOperationDecorator() {
        return services.get(ListenerBuildOperationDecorator.class);
    }

    UserCodeApplicationContext createUserCodeApplicationContext() {
        return services.get(UserCodeApplicationContext.class);
    }

    BuildOperationNotificationListenerRegistrar createBuildOperationNotificationListenerRegistrar() {
        return buildOperationNotificationBridge.getRegistrar();
    }

    BuildOperationNotificationValve createBuildOperationNotificationValve() {
        return buildOperationNotificationBridge.getValve();
    }

    CollectionCallbackActionDecorator createDomainObjectCollectioncallbackActionDecorator(BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
        return services.get(CollectionCallbackActionDecorator.class);
    }

    @Override
    public void close() throws IOException {
        new CompositeStoppable().add(
            buildOperationTrace,
            buildOperationNotificationBridge,
            loggingBuildOperationProgressBroadcaster,
            services
        ).stop();
    }

    private class Services extends DefaultServiceRegistry {

        public Services(ServiceRegistry parent) {
            super(parent);
        }

        GradleLauncherFactory createGradleLauncherFactory(GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry) {
            return new DefaultGradleLauncherFactory(
                userHomeDirServiceRegistry,
                CrossBuildSessionScopeServices.this
            );
        }

        WorkerLeaseService createWorkerLeaseService(ResourceLockCoordinationService resourceLockCoordinationService, ParallelismConfigurationManager parallelismConfigurationManager) {
            return new DefaultWorkerLeaseService(resourceLockCoordinationService, parallelismConfigurationManager);
        }

        BuildOperationExecutor createBuildOperationExecutor(
            Clock clock,
            ProgressLoggerFactory progressLoggerFactory,
            WorkerLeaseService workerLeaseService,
            ExecutorFactory executorFactory,
            ParallelismConfigurationManager parallelismConfigurationManager,
            BuildOperationIdFactory buildOperationIdFactory
        ) {
            return new DefaultBuildOperationExecutor(
                buildOperationListenerManager.getBroadcaster(),
                clock,
                progressLoggerFactory,
                new DefaultBuildOperationQueueFactory(workerLeaseService),
                executorFactory,
                parallelismConfigurationManager,
                buildOperationIdFactory
            );
        }

        UserCodeApplicationContext createUserCodeApplicationContext() {
            return new DefaultUserCodeApplicationContext();
        }

        ListenerBuildOperationDecorator createListenerBuildOperationDecorator(BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
            return new DefaultListenerBuildOperationDecorator(buildOperationExecutor, userCodeApplicationContext);
        }

        CollectionCallbackActionDecorator createDomainObjectCollectioncallbackActionDecorator(BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
            return new DefaultCollectionCallbackActionDecorator(buildOperationExecutor, userCodeApplicationContext);
        }
    }
}
