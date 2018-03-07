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
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.CategorisedOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;

public class LoggingBuildOperationNotificationBridge implements Stoppable, OutputEventListener {

    private final LoggingManagerInternal loggingManagerInternal;
    private final BuildOperationListener buildOperationListener;

    public LoggingBuildOperationNotificationBridge(LoggingManagerInternal loggingManagerInternal, BuildOperationListener buildOperationListener) {
        this.loggingManagerInternal = loggingManagerInternal;
        this.buildOperationListener = buildOperationListener;
        loggingManagerInternal.addOutputEventListener(this);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof RenderableOutputEvent) {
            RenderableOutputEvent renderableOutputEvent = (RenderableOutputEvent) event;
            if (renderableOutputEvent.getBuildOperationId() == null) {
                return;
            }
            if (renderableOutputEvent instanceof StyledTextOutputEvent || renderableOutputEvent instanceof LogEvent) {
                emit(renderableOutputEvent, renderableOutputEvent.getBuildOperationId());
            }
        } else if (event instanceof ProgressStartEvent) {
            ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
            if (progressStartEvent.getBuildOperationId() == null) {
                return;
            }
            if (progressStartEvent.getLoggingHeader() == null) {
                // If the event has no logging header, it doesn't manifest as console output.
                return;
            }
            emit(progressStartEvent, progressStartEvent.getBuildOperationId());
        }
    }

    private void emit(CategorisedOutputEvent event, OperationIdentifier buildOperationId) {
        buildOperationListener.progress(
            buildOperationId,
            new OperationProgressEvent(event.getTimestamp(), event)
        );
    }

    @Override
    public void stop() {
        loggingManagerInternal.removeOutputEventListener(this);
    }
}
