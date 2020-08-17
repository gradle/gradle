/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class UnmanagedBuildOperationWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnmanagedBuildOperationWrapper.class);

    private final BuildOperationListener listener;
    private final Clock clock;
    private final CurrentBuildOperationRef currentBuildOperationRef;

    public UnmanagedBuildOperationWrapper(BuildOperationListener listener, Clock clock, CurrentBuildOperationRef currentBuildOperationRef) {
        this.listener = listener;
        this.clock = clock;
        this.currentBuildOperationRef = currentBuildOperationRef;
    }

    public void runWithUnmanagedSupport(@Nullable BuildOperationState defaultParent, Consumer<BuildOperationState> work) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation(defaultParent);
        try {
            work.accept(parent);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    public <T> T callWithUnmanagedSupport(@Nullable BuildOperationState defaultParent, Function<BuildOperationState, T> work) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation(defaultParent);
        try {
            return work.apply(parent);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    @Nullable
    private BuildOperationState maybeStartUnmanagedThreadOperation(@Nullable BuildOperationState parent) {
        if (parent == null && !GradleThread.isManaged()) {
            BuildOperationState unmanaged = UnmanagedThreadOperation.create(clock.getCurrentTime());
            unmanaged.setRunning(true);
            currentBuildOperationRef.set(unmanaged);
            listener.started(unmanaged.getDescription(), new OperationStartEvent(unmanaged.getStartTime()));
            return unmanaged;
        } else {
            return parent;
        }
    }

    private void maybeStopUnmanagedThreadOperation(@Nullable BuildOperationState current) {
        if (current instanceof UnmanagedThreadOperation) {
            try {
                listener.finished(current.getDescription(), new OperationFinishEvent(current.getStartTime(), clock.getCurrentTime(), null, null));
            } finally {
                currentBuildOperationRef.set(null);
                current.setRunning(false);
            }
        }
    }

    private static class UnmanagedThreadOperation extends BuildOperationState {

        private static final AtomicLong UNMANAGED_THREAD_OPERATION_COUNTER = new AtomicLong(-1);

        private static UnmanagedThreadOperation create(long currentTime) {
            // TODO:pm Move this to WARN level once we fixed maven-publish, see gradle/gradle#1662
            LOGGER.debug("WARNING No operation is currently running in unmanaged thread: {}", Thread.currentThread().getName());
            OperationIdentifier id = new OperationIdentifier(UNMANAGED_THREAD_OPERATION_COUNTER.getAndDecrement());
            String displayName = "Unmanaged thread operation #" + id + " (" + Thread.currentThread().getName() + ')';
            return new UnmanagedThreadOperation(BuildOperationDescriptor.displayName(displayName).build(id, null), currentTime);
        }

        private UnmanagedThreadOperation(BuildOperationDescriptor descriptor, long startTime) {
            super(descriptor, startTime);
        }
    }
}
