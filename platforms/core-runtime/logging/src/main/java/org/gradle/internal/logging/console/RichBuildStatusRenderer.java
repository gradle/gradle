/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.NonNullApi;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

/**
 * <p>This listener displays nothing unless it receives periodic {@link UpdateNowEvent} clock events.</p>
 */
@NonNullApi
public class RichBuildStatusRenderer extends BuildStatusRenderer {
    public static final int PROGRESS_BAR_WIDTH = 13;
    public static final String PROGRESS_BAR_PREFIX = "<";
    public static final char PROGRESS_BAR_COMPLETE_CHAR = '=';
    public static final char PROGRESS_BAR_INCOMPLETE_CHAR = '-';
    public static final String PROGRESS_BAR_SUFFIX = ">";

    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;

    // What actually shows up on the console
    private ProgressBar progressBar;

    public RichBuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData) {
        super(listener);
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
    }

    @Override
    protected void renderNow(long now) {
        if (progressBar != null) {
            buildStatusLabel.setText(progressBar.formatProgress(getTimerEnabled(), now - getBuildStartTimestamp()));
        }
        console.flush();
    }

    @Override
    protected void phaseStarted(ProgressStartEvent startEvent, Phase phase) {
        progressBar = newProgressBar(phase.name().toUpperCase(), 0, startEvent.getTotalProgress());
    }

    @Override
    protected void phaseHasMoreProgress(ProgressStartEvent progressStartEvent) {
        progressBar.moreProgress(progressStartEvent.getTotalProgress());
    }

    @Override
    protected void phaseProgressed(ProgressCompleteEvent progressEvent) {
        if (progressBar != null) {
            progressBar.update(progressEvent.isFailed());
        }
    }

    @Override
    protected void buildEnded() {
        progressBar = newProgressBar("WAITING", 0, 1);
    }

    @VisibleForTesting
    public ProgressBar newProgressBar(String initialSuffix, int initialProgress, int totalProgress) {
        return new ProgressBar(consoleMetaData,
            PROGRESS_BAR_PREFIX,
            PROGRESS_BAR_WIDTH,
            PROGRESS_BAR_SUFFIX,
            PROGRESS_BAR_COMPLETE_CHAR,
            PROGRESS_BAR_INCOMPLETE_CHAR,
            initialSuffix, initialProgress, totalProgress);
    }
}
