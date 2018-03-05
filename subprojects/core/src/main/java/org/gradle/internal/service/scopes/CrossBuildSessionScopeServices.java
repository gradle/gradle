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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.operations.notify.BuildOperationNotificationBridge;
import org.gradle.internal.operations.trace.BuildOperationTrace;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.BuildProgressLogger;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.progress.DefaultBuildOperationListenerManager;
import org.gradle.internal.progress.DelegatingBuildOperationExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
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

    private final DefaultGradleLauncherFactory gradleLauncherFactory;
    private final WorkerLeaseService workerLeaseService;
    private final WorkerLeaseService stopShieldWorkerLeaseService;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final ListenerManager crossSessionListenerManager;

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationExecutor stopShieldBuildOperationExecutor;
    private final BuildOperationTrace buildOperationTrace;
    private final BuildOperationNotificationBridge buildOperationNotificationBridge;

    public CrossBuildSessionScopeServices(ServiceRegistry parent, StartParameter startParameter) {
        ProgressLoggerFactory progressLoggerFactory = parent.get(ProgressLoggerFactory.class);
        ResourceLockCoordinationService resourceLockCoordinationService = parent.get(ResourceLockCoordinationService.class);
        ParallelismConfigurationManager parallelismConfigurationManager = parent.get(ParallelismConfigurationManager.class);

        this.gradleLauncherFactory = new DefaultGradleLauncherFactory(
            parent.get(GradleUserHomeScopeServiceRegistry.class),
            parent.get(BuildProgressLogger.class),
            this
        );

        this.workerLeaseService = new DefaultWorkerLeaseService(
            resourceLockCoordinationService,
            parallelismConfigurationManager
        );

        this.stopShieldWorkerLeaseService = new StopShieldingWorkerLeaseService(workerLeaseService);

        this.crossSessionListenerManager = parent.get(ListenerManager.class).createChild();
        this.buildOperationListenerManager = new DefaultBuildOperationListenerManager(crossSessionListenerManager);

        this.buildOperationExecutor = new DefaultBuildOperationExecutor(
            buildOperationListenerManager.getBroadcaster(),
            parent.get(Clock.class),
            progressLoggerFactory,
            new DefaultBuildOperationQueueFactory(workerLeaseService),
            parent.get(ExecutorFactory.class),
            resourceLockCoordinationService,
            parallelismConfigurationManager,
            parent.get(BuildOperationIdFactory.class)
        );

        this.buildOperationNotificationBridge = new BuildOperationNotificationBridge(buildOperationListenerManager);

        this.stopShieldBuildOperationExecutor = new DelegatingBuildOperationExecutor(buildOperationExecutor);

        this.buildOperationTrace = new BuildOperationTrace(startParameter, crossSessionListenerManager);
    }

    @Override
    public void close() throws IOException {
        new CompositeStoppable().add(
            buildOperationExecutor,
            workerLeaseService,
            buildOperationTrace,
            buildOperationNotificationBridge.getStoppable()
        ).stop();
    }

    GradleLauncherFactory createGradleLauncherFactory() {
        return gradleLauncherFactory;
    }

    WorkerLeaseService createWorkerLeaseService() {
        return stopShieldWorkerLeaseService;
    }

    BuildOperationListenerManager createBuildOperationListenerManager() {
        return buildOperationListenerManager;
    }

    BuildOperationExecutor createBuildOperationExecutor() {
        return stopShieldBuildOperationExecutor;
    }

    BuildOperationNotificationBridge createBuildOperationNotificationBridge() {
        return buildOperationNotificationBridge;
    }

    ListenerManager createListenerManager() {
        // Create a child for each session mixed into
        return crossSessionListenerManager.createChild();
    }

}
