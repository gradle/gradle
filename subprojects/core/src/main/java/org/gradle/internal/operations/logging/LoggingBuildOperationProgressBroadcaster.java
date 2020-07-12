/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.logging;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;

/**
 * Emits build operation progress for events that represent logging.
 *
 * Uses the existing {@link OutputEventListener} to observe events that <i>may</i> cause console output,
 * and emits build operation progress events for those that do cause console output.
 *
 * Currently, the only audience of these events is the build scan plugin.
 * It is concerned with recreating the <i>plain</i> console for an invocation,
 * and associating logging output with tasks, projects, and other logical entities.
 * It does not attempt to emulate the rich console.
 *
 * This solution has some quirks due to how the console output subsystem in Gradle has evolved.
 *
 * An “output event” effectively represents something of interest happening that
 * some observer may wish to know about in order to visualise what is happening, e.g. a console renderer.
 * We are integrating at this level, but imposing different semantics.
 * We only broadcast the subset of events that influence the “plain console”, because this is all we need right now.
 * The build scan infrastructure has some knowledge of how different versions of Gradle respond to these events
 * with regard to console rendering and effectively emulate.
 *
 * Ideally, we would emit a more concrete model.
 * This would be something like more clearly separating logging output from “user code” from Gradle's “UI” output,
 * and separately observing it from rendering instructions.
 * This may come later.
 *
 * @since 4.7
 */
public class LoggingBuildOperationProgressBroadcaster implements Stoppable, OutputEventListener {

    private final OutputEventListenerManager outputEventListenerManager;
    private final BuildOperationProgressEventEmitter progressEventEmitter;

    public LoggingBuildOperationProgressBroadcaster(OutputEventListenerManager outputEventListenerManager, BuildOperationProgressEventEmitter progressEventEmitter) {
        this.outputEventListenerManager = outputEventListenerManager;
        this.progressEventEmitter = progressEventEmitter;
        outputEventListenerManager.setListener(this);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof StyledTextOutputEvent) {
            onOutputOf((StyledTextOutputEvent) event);
        } else if (event instanceof LogEvent) {
            onOutputOf((LogEvent) event);
        } else if (event instanceof ProgressStartEvent) {
            onOutputOf((ProgressStartEvent) event);
        }
    }

    private void onOutputOf(StyledTextOutputEvent event) {
        doEmit(event.getTimestamp(), event, event.getBuildOperationId());
    }

    private void onOutputOf(LogEvent event) {
        doEmit(event.getTimestamp(), event, event.getBuildOperationId());
    }

    private void onOutputOf(ProgressStartEvent event) {
        if (event.getLoggingHeader() != null) {
            // if the event has no logging header, it doesn't manifest as console output.
            doEmit(event.getTimestamp(), event, event.getBuildOperationId());
        }
    }

    private void doEmit(long timestamp, Object event, @Nullable OperationIdentifier buildOperationId) {
        if (buildOperationId == null) {
            progressEventEmitter.emitForCurrentOrRootOperationIfWithin(timestamp, event);
        } else {
            progressEventEmitter.emit(buildOperationId, timestamp, event);
        }
    }

    @Override
    public void stop() {
        outputEventListenerManager.removeListener(this);
    }

}
