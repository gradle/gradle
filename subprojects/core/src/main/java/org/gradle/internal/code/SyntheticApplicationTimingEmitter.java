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

package org.gradle.internal.code;

import org.gradle.api.internal.ExecuteDomainObjectCollectionCallbackBuildOperationType;
import org.gradle.internal.buildtree.BuildTreeLifecycleListener;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationState;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks all user code applications that have been executed for a single build invocation.
 */
@ServiceScope(Scope.BuildTree.class)
public class SyntheticApplicationTimingEmitter implements BuildTreeLifecycleListener {

    private final BuildOperationIdFactory buildOperationIdFactory;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final UserCodeApplicationRegistry applicationRegistry;

    @Inject
    public SyntheticApplicationTimingEmitter(
        BuildOperationIdFactory buildOperationIdFactory,
        BuildOperationListenerManager buildOperationListenerManager,
        UserCodeApplicationRegistry applicationRegistry
    ) {
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.applicationRegistry = applicationRegistry;
    }

    @Override
    public void beforeStop() {
        BuildOperationState parent = (BuildOperationState) CurrentBuildOperationRef.instance().get();
        if (parent == null) {
            throw new IllegalStateException("Cannot execute build operation as no current operation is running.");
        } else if (!parent.isRunning()) {
            throw new IllegalStateException("Cannot execute build operation as the current operation is not running.");
        }

        // When the build completes, generate synthetic build operation events, each with duration
        // equal to the total time spend executing collection callbacks for a given application ID.
        // In prior versions of Gradle, we would emit one of these events synchronously each time we
        // executed an action against a container. This proved to be incredibly expensive. Emitting
        // synthetic events in this fashion allows us to maintain the same contract with DV while
        // emitting significantly fewer events.

        long startTime = parent.getStartTime();
        OperationIdentifier parentId = parent.getId();
        BuildOperationListener listener = buildOperationListenerManager.getBroadcaster();
        BuildOperationDescriptor.Builder descriptorBuilder = BuildOperationDescriptor
            .displayName("Execute container callback action");

        for (List<UserCodeApplicationContext.Application> applicationsForProject : applicationRegistry.consumeApplications().values()) {
            for (UserCodeApplicationContext.Application application : applicationsForProject) {
                long collectionCallbackDurationNs = application.getDurationNsForType(UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK);
                long collectionCallbackDurationMs = TimeUnit.NANOSECONDS.toMillis(collectionCallbackDurationNs);
                if (collectionCallbackDurationMs > 0) {
                    long applicationId = application.getId().longValue();
                    BuildOperationDescriptor descriptor = descriptorBuilder
                        .details(new SyntheticExecuteDomainObjectCollectionCallbackBuildOperationDetails(applicationId))
                        .build(new OperationIdentifier(buildOperationIdFactory.nextId()), parentId);

                    long endTime = startTime + collectionCallbackDurationMs;
                    listener.started(descriptor, new OperationStartEvent(startTime));
                    listener.finished(descriptor, new OperationFinishEvent(startTime, endTime, null, ExecuteDomainObjectCollectionCallbackBuildOperationType.RESULT));
                }
            }
        }
    }

    private static class SyntheticExecuteDomainObjectCollectionCallbackBuildOperationDetails implements ExecuteDomainObjectCollectionCallbackBuildOperationType.Details {

        private final long applicationId;

        private SyntheticExecuteDomainObjectCollectionCallbackBuildOperationDetails(long applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public long getApplicationId() {
            return applicationId;
        }

    }

}
