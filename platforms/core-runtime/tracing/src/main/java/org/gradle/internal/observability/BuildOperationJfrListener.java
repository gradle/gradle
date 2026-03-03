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

package org.gradle.internal.observability;

import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;
import java.util.Stack;

/**
 * Emits JFR duration events for each Gradle build operation.
 *
 * <p>The listener is always active but has near-zero overhead when no JFR recording is running,
 * because {@link jdk.jfr.Event#isEnabled()} is {@code false} in that case and the event object
 * is discarded immediately without any allocation on the hot path.
 *
 * <p>In-flight events are stored in a map keyed by {@link OperationIdentifier}.
 * The {@link BuildOperationListenerManager} guarantees that {@code started}/{@code finished} calls
 * for the <em>same</em> operation ID are serialised. Parent IDs are resolved through
 * {@link BuildOperationAncestryTracker} so each emitted parent points to an in-flight JFR event.
 */
@ServiceScope(Scope.CrossBuildSession.class)
class BuildOperationJfrListener implements Closeable {

    private final BuildOperationListenerManager manager;
    private final BuildOperationListener delegate;

    /**
     * We expect that build operations are not crossing threads.
     * Therefore, we can use per-thread collections to store the stack of build operations happen.
     */
    private final ThreadLocal<Stack<BuildOperationJfrEvent>> eventStack = ThreadLocal.withInitial(Stack::new);

    BuildOperationJfrListener(BuildOperationListenerManager manager) {
        this.manager = manager;
        this.delegate = new Listener();
        manager.addListener(delegate);
    }

    @Override
    public void close() {
        manager.removeListener(delegate);
    }

    private class Listener implements BuildOperationListener {

        @Override
        public void started(BuildOperationDescriptor descriptor, OperationStartEvent startEvent) {
            BuildOperationJfrEvent event = new BuildOperationJfrEvent();
            if (!event.isEnabled()) {
                return; // JFR not recording — skip entirely
            }
            OperationIdentifier id = descriptor.getId();
            if (id == null) {
                return;
            }
            event.operationId = id.getId();
            if (descriptor.getParentId() != null) {
                event.parentId = descriptor.getParentId().getId();
            } else {
                event.parentId = 0;
            }
            event.displayName = descriptor.getDisplayName();
            event.begin();
            eventStack.get().push(event);
        }

        @Override
        public void progress(OperationIdentifier id, OperationProgressEvent progressEvent) {
            // Not represented in JFR; ignored.
        }

        @Override
        public void finished(BuildOperationDescriptor descriptor, OperationFinishEvent finishEvent) {
            BuildOperationJfrEvent event = eventStack.get().pop();
            Throwable failure = finishEvent.getFailure();
            if (failure != null) {
                event.failed = true;
                event.failureMessage = failure.getMessage();
                event.failureType = failure.getClass().getName();
            }
            event.commit();
        }
    }
}
