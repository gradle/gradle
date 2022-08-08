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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.events.CategorisedOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

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
@ServiceScope(Scopes.BuildSession.class)
public class LoggingBuildOperationProgressBroadcaster implements Stoppable, OutputEventListener {

    private final OutputEventListenerManager outputEventListenerManager;
    private final BuildOperationProgressEventEmitter progressEventEmitter;

    @VisibleForTesting
    OperationIdentifier rootBuildOperation;

    public LoggingBuildOperationProgressBroadcaster(OutputEventListenerManager outputEventListenerManager, BuildOperationProgressEventEmitter progressEventEmitter) {
        this.outputEventListenerManager = outputEventListenerManager;
        this.progressEventEmitter = progressEventEmitter;
        outputEventListenerManager.setListener(this);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof RenderableOutputEvent) {
            RenderableOutputEvent renderableOutputEvent = (RenderableOutputEvent) event;
            OperationIdentifier operationIdentifier = renderableOutputEvent.getBuildOperationId();
            if (operationIdentifier == null) {
                if (rootBuildOperation == null) {
                    return;
                }
                operationIdentifier = rootBuildOperation;
            }

            if (renderableOutputEvent instanceof StyledTextOutputEvent || renderableOutputEvent instanceof LogEvent) {
                emit(renderableOutputEvent, operationIdentifier);
            }
        } else if (event instanceof ProgressStartEvent) {
            ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
            if (progressStartEvent.getLoggingHeader() == null) {
                return; // If the event has no logging header, it doesn't manifest as console output.
            }
            OperationIdentifier operationIdentifier = progressStartEvent.getBuildOperationId();
            if (operationIdentifier == null && rootBuildOperation != null) {
                operationIdentifier = rootBuildOperation;
            }
            emit(progressStartEvent, operationIdentifier);
        }
    }

    private void emit(CategorisedOutputEvent event, OperationIdentifier buildOperationId) {
        progressEventEmitter.emit(
            buildOperationId,
            event.getTimestamp(),
            event
        );
    }

    @Override
    public void stop() {
        outputEventListenerManager.removeListener(this);
    }

    public void rootBuildOperationStarted() {
        rootBuildOperation = CurrentBuildOperationRef.instance().getId();
    }
}
