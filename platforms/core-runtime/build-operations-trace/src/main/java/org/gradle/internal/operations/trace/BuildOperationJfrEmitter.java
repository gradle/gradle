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

package org.gradle.internal.operations.trace;

import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emits a JFR event per Gradle build operation into whatever JFR recording is active.
 *
 * <p>Enabled with {@code -Dorg.gradle.internal.operations.jfr=true}, mirroring the file-based
 * {@link BuildOperationTrace}. This only emits events; it does not start or manage a recording.
 * The events are picked up by any active recording — e.g. one started by the user with
 * {@code -XX:StartFlightRecording}. When disabled, no listener is registered and there is no overhead.
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class BuildOperationJfrEmitter implements Closeable {

    public static final String SYSPROP = "org.gradle.internal.operations.jfr";

    static final InternalOption<Boolean> ENABLED_OPTION = InternalOptions.ofBoolean(SYSPROP, false);

    private final BuildOperationListenerManager manager;
    private final @Nullable BuildOperationListener listener;

    /**
     * In-flight events keyed by operation id.
     *
     * <p>A build operation may start on one thread and finish on another.
     * The {@link BuildOperationListener} contract only guarantees the two signals are serialized, not that they share a thread.
     * A per-thread map would silently drop such operations, so we key across threads.
     */
    private final Map<Long, BuildOperationJfrEvent> inFlightEvents = new ConcurrentHashMap<>();

    public BuildOperationJfrEmitter(InternalOptions options, BuildOperationListenerManager manager) {
        Listener listener = null;
        if (options.getBoolean(ENABLED_OPTION)) {
            listener = new Listener();
            manager.addListener(listener);
        }

        this.manager = manager;
        this.listener = listener;
    }

    @Override
    public void close() {
        if (listener != null) {
            manager.removeListener(listener);
        }
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
            OperationIdentifier parentId = descriptor.getParentId();
            event.parentId = parentId != null ? parentId.getId() : 0;
            event.displayName = descriptor.getDisplayName();
            event.gradleStartTime = startEvent.getStartTime();
            event.begin();
            inFlightEvents.put(id.getId(), event);
        }

        @Override
        public void progress(OperationIdentifier id, OperationProgressEvent progressEvent) {
            // Not represented in JFR; ignored.
        }

        @Override
        public void finished(BuildOperationDescriptor descriptor, OperationFinishEvent finishEvent) {
            OperationIdentifier id = descriptor.getId();
            if (id == null) {
                return;
            }
            BuildOperationJfrEvent event = inFlightEvents.remove(id.getId());
            if (event == null) {
                return; // not recorded at start (recording was not yet active)
            }
            event.gradleEndTime = finishEvent.getEndTime();
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
