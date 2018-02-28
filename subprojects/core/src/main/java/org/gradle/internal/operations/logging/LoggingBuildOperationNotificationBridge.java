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
import org.gradle.internal.logging.buildoperation.OutputBuildOperationProgressDetails;
import org.gradle.internal.logging.events.CategorisedOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationProgressEvent;

public class LoggingBuildOperationNotificationBridge implements Stoppable, OutputEventListener {

    private final LoggingManagerInternal loggingManagerInternal;
    private final BuildOperationListener buildOperationListenerBroadcaster;

    public LoggingBuildOperationNotificationBridge(LoggingManagerInternal loggingManagerInternal, BuildOperationListener buildOperationListener) {
        this.loggingManagerInternal = loggingManagerInternal;
        this.buildOperationListenerBroadcaster = buildOperationListener;
        loggingManagerInternal.addOutputEventListener(this);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof RenderableOutputEvent) {
            RenderableOutputEvent renderableOutputEvent = (RenderableOutputEvent) event;
            forwardAsBuildOperationProgress(renderableOutputEvent, renderableOutputEvent.getBuildOperationId());
        } else if (event instanceof ProgressStartEvent) {
            ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
            forwardAsBuildOperationProgress(progressStartEvent, progressStartEvent.getBuildOperationId());
        }
    }

    private void forwardAsBuildOperationProgress(CategorisedOutputEvent renderableOutputEvent, Object buildOperationId) {
        if (buildOperationId != null) {
            buildOperationListenerBroadcaster.progress(
                buildOperationId,
                new OperationProgressEvent(renderableOutputEvent.getTimestamp(), filter(renderableOutputEvent))
            );
        }
    }

    public static Object filter(CategorisedOutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            final ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
            if (progressStartEvent.getLoggingHeader() != null || expectedFromBuildScanPlugin(progressStartEvent)) {
                return progressStartEvent;
            }
        } else if (event instanceof OutputBuildOperationProgressDetails) {
            return event;
        }
        return null;
    }

    /**
     * workaround to ensure Download / Upload console log can be captured.
     *
     * Problem to workaround:
     *
     * When Download operation is triggered we generate 2 progress events: 1. from the build operation executer including a referenced build operation but no logging header 2. from
     * `AbstractProgressLoggingHandler` that includes a logging header but no referenced build operation
     *
     * By default only progress events with associated build operations are forwarded to the build operation listener.
     *
     * The build scan plugin by default only handles progress starte events _with_ a logging header.
     *
     * This workaround bypasses the 2nd limitations.
     */

    private static boolean expectedFromBuildScanPlugin(ProgressStartEvent progressStartEvent) {
        return progressStartEvent.getDescription().startsWith("Download")
            || progressStartEvent.getDescription().startsWith("Upload");

    }

    @Override
    public void stop() {
        loggingManagerInternal.removeOutputEventListener(this);
    }
}
