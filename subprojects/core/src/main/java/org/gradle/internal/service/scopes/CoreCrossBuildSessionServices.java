/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultCollectionCallbackActionDecorator;
import org.gradle.configuration.internal.DefaultDynamicCallContextTracker;
import org.gradle.configuration.internal.DefaultListenerBuildOperationDecorator;
import org.gradle.configuration.internal.DynamicCallContextTracker;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.internal.code.DefaultUserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.concurrent.DefaultWorkerLimits;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.WorkerLimits;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationExecutor;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationBridge;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.operations.trace.BuildOperationTrace;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.work.DefaultWorkerLeaseService;
import org.gradle.internal.work.WorkerLeaseService;

public class CoreCrossBuildSessionServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration registration) {
        registration.add(ResourceLockCoordinationService.class, DefaultResourceLockCoordinationService.class);
        registration.add(DefaultWorkerLeaseService.class);
        registration.add(DynamicCallContextTracker.class, DefaultDynamicCallContextTracker.class);
    }

    @Provides
    WorkerLimits createWorkerLimits(CrossBuildSessionParameters buildSessionParameters) {
        return new DefaultWorkerLimits(buildSessionParameters.getStartParameter().getMaxWorkerCount());
    }

    @Provides
    BuildOperationExecutor createBuildOperationExecutor(
        BuildOperationRunner buildOperationRunner,
        CurrentBuildOperationRef currentBuildOperationRef,
        WorkerLeaseService workerLeaseService,
        ExecutorFactory executorFactory,
        WorkerLimits workerLimits
    ) {
        return new DefaultBuildOperationExecutor(
            buildOperationRunner,
            currentBuildOperationRef,
            new DefaultBuildOperationQueueFactory(workerLeaseService),
            executorFactory,
            workerLimits
        );
    }

    @Provides
    UserCodeApplicationContext createUserCodeApplicationContext() {
        return new DefaultUserCodeApplicationContext();
    }

    @Provides
    ListenerBuildOperationDecorator createListenerBuildOperationDecorator(BuildOperationRunner buildOperationRunner, UserCodeApplicationContext userCodeApplicationContext) {
        return new DefaultListenerBuildOperationDecorator(buildOperationRunner, userCodeApplicationContext);
    }

    @Provides
    CollectionCallbackActionDecorator createDomainObjectCollectioncallbackActionDecorator(BuildOperationRunner buildOperationRunner, UserCodeApplicationContext userCodeApplicationContext) {
        return new DefaultCollectionCallbackActionDecorator(buildOperationRunner, userCodeApplicationContext);
    }

    @Provides
    LoggingBuildOperationProgressBroadcaster createLoggingBuildOperationProgressBroadcaster(OutputEventListenerManager outputEventListenerManager, BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        return new LoggingBuildOperationProgressBroadcaster(outputEventListenerManager, buildOperationProgressEventEmitter);
    }

    @Provides
    BuildOperationTrace createBuildOperationTrace(BuildOperationListenerManager buildOperationListenerManager, CrossBuildSessionParameters buildSessionParameters) {
        return new BuildOperationTrace(buildSessionParameters.getStartParameter(), buildOperationListenerManager);
    }

    @Provides
    BuildOperationNotificationBridge createBuildOperationNotificationBridge(BuildOperationListenerManager buildOperationListenerManager, ListenerManager generalListenerManager) {
        return new BuildOperationNotificationBridge(buildOperationListenerManager, generalListenerManager);
    }

    @Provides
    BuildOperationNotificationValve createBuildOperationNotificationValve(BuildOperationNotificationBridge buildOperationNotificationBridge) {
        return buildOperationNotificationBridge.getValve();
    }
}
