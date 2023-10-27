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

import org.gradle.api.NonNullApi;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@NonNullApi
public final class PlainBuildStatusRenderer extends BuildStatusRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlainBuildStatusRenderer.class);

    private final OutputEventListener listener;
    private boolean isFailing;
    private int currentProgress;
    private int totalProgress;
    private Phase lastPhase;
    private int lastRenderedProgressPct = -1;
    private boolean lastFailing = false;
    private ExecutorService deadlockPreventer;


    public PlainBuildStatusRenderer(OutputEventListener listener) {
        super(listener);
        this.listener = listener;
    }

    @Override
    protected void renderNow(long now) {
        if (getCurrentPhase() == null) {
            return;
        }
        int progressPct = totalProgress == 0 ? 0 : (int) (currentProgress * 100.0 / totalProgress);

        if (getCurrentPhase().equals(lastPhase) && progressPct == lastRenderedProgressPct && isFailing == lastFailing) {
            return;
        }

        lastPhase = getCurrentPhase();
        lastRenderedProgressPct = progressPct;
        lastFailing = isFailing;

        String rendered = String.format(Locale.US, "> Progress: %s %d%%",
            getCurrentPhase().name().toUpperCase(), progressPct);
        if (isFailing) {
            rendered += " (FAILING)";
        }

        listener.onOutput(new LogEvent(now, "progress", LogLevel.LIFECYCLE, rendered, null));
    }

    @Override
    protected void phaseStarted(ProgressStartEvent startEvent, Phase phase) {
        currentProgress = 0;
        totalProgress = startEvent.getTotalProgress();
    }

    @Override
    protected void phaseHasMoreProgress(ProgressStartEvent progressStartEvent) {
        totalProgress += progressStartEvent.getTotalProgress();
    }

    @Override
    protected void phaseProgressed(ProgressCompleteEvent progressEvent) {
        isFailing |= progressEvent.isFailed();
        currentProgress++;

        if (currentProgress > totalProgress) {
            if (deadlockPreventer == null) {
                deadlockPreventer = Executors.newSingleThreadExecutor();
            }
            // do not do this directly or a deadlock happens
            // to prevent that deadlock, execute it separately in another thread
            deadlockPreventer.submit(new OverflowedProgressLogger());
        }
    }

    @Override
    protected void buildEnded() {
        currentProgress = 0;
        totalProgress = 0;
    }

    @NonNullApi
    private final class OverflowedProgressLogger implements Runnable {
        // Save these properties so that they do not change before the thread runs
        private final int savedCurrentProgress = currentProgress;
        private final int savedTotalProgress = totalProgress;

        @Override
        public void run() {
            LOGGER.warn("More progress was logged than there should be ({} > {})", savedCurrentProgress, savedTotalProgress);
        }
    }
}
