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

package org.gradle.internal.logging.console;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.time.TimeProvider;

public class BuildStatusRenderer implements OutputEventListener {
    public static final int PROGRESS_BAR_WIDTH = 13;
    public static final String PROGRESS_BAR_PREFIX = "<";
    public static final char PROGRESS_BAR_COMPLETE_CHAR = '=';
    public static final char PROGRESS_BAR_INCOMPLETE_CHAR = '-';
    public static final String PROGRESS_BAR_SUFFIX = ">";

    public static final String BUILD_PROGRESS_CATEGORY = "org.gradle.internal.progress.BuildProgressLogger";

    private final OutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private final TimeProvider timeProvider;
    private long currentPhaseProgressOperationId;

    // What actually shows up on the console
    private ProgressBar progressBar;

    // Used to maintain timer
    private long buildStartTimestamp;
    private boolean timerEnabled;

    public BuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, TimeProvider timeProvider) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.timeProvider = timeProvider;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.getBuildOperationId() != null && startEvent.getParentBuildOperationId() == null) {
                buildStarted(startEvent);
            } else if (BUILD_PROGRESS_CATEGORY.equals(startEvent.getCategory())) {
                phaseStarted(startEvent);
            }
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            if (isPhaseProgressEvent(completeEvent.getProgressOperationId())) {
                phaseEnded(completeEvent);
            }
        } else if (event instanceof ProgressEvent) {
            ProgressEvent progressEvent = (ProgressEvent) event;
            if (isPhaseProgressEvent(progressEvent.getProgressOperationId())) {
                phaseProgressed(progressEvent);
            }
        }

        listener.onOutput(event);

        if (event instanceof UpdateNowEvent || event instanceof EndOutputEvent) {
            renderNow(timeProvider.getCurrentTime());
        }
    }

    private boolean isPhaseProgressEvent(OperationIdentifier progressOpId) {
        return progressOpId.getId() == currentPhaseProgressOperationId;
    }

    private void renderNow(long now) {
        if (progressBar != null) {
            buildStatusLabel.setText(progressBar.formatProgress(consoleMetaData.getCols(), timerEnabled, now - buildStartTimestamp));
        }
        console.flush();
    }

    private void buildStarted(ProgressStartEvent startEvent) {
        buildStartTimestamp = timeProvider.getCurrentTime();
    }

    private void phaseStarted(ProgressStartEvent progressStartEvent) {
        timerEnabled = true;
        currentPhaseProgressOperationId = progressStartEvent.getProgressOperationId().getId();
        progressBar = newProgressBar(progressStartEvent.getShortDescription(), 0, progressStartEvent.getTotalProgress());
    }

    private void phaseProgressed(ProgressEvent progressEvent) {
        if (progressBar != null) {
            progressBar.update(progressEvent.isFailing());
        }
    }

    private void phaseEnded(ProgressCompleteEvent progressCompleteEvent) {
        progressBar = newProgressBar(progressCompleteEvent.getStatus(), 0, 1);
        timerEnabled = false;
    }

    @VisibleForTesting
    public ProgressBar newProgressBar(String initialSuffix, int initialProgress, int totalProgress) {
        return new ProgressBar(PROGRESS_BAR_PREFIX,
            PROGRESS_BAR_WIDTH,
            PROGRESS_BAR_SUFFIX,
            PROGRESS_BAR_COMPLETE_CHAR,
            PROGRESS_BAR_INCOMPLETE_CHAR,
            initialSuffix, initialProgress, totalProgress);
    }
}
