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
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.PhaseProgressStartEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.format.ProgressBarFormatter;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.Span;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.time.TimeProvider;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BuildStatusRenderer extends BatchOutputEventListener {
    private static final long RENDER_NOW_PERIOD_MILLISECONDS = 250;
    private static final int PROGRESS_BAR_WIDTH = 13;
    private static final String PROGRESS_BAR_PREFIX = "<";
    private static final char PROGRESS_BAR_COMPLETE_CHAR = '=';
    private static final char PROGRESS_BAR_INCOMPLETE_CHAR = '-';
    private static final String PROGRESS_BAR_SUFFIX = ">";
    private final BatchOutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private final TimeProvider timeProvider;
    private final ScheduledExecutorService executor;
    private final TersePrettyDurationFormatter elapsedTimeFormatter = new TersePrettyDurationFormatter();
    private final Set<OperationIdentifier> startedPhaseChildren = new LinkedHashSet<OperationIdentifier>();
    private final Object lock = new Object();
    private String currentBuildStatus;
    private OperationIdentifier rootOperationId;
    private OperationIdentifier currentPhaseProgressOperationId;
    private long buildStartTimestamp;
    private ScheduledFuture future;
    private ProgressBarFormatter progressBarFormatter;

    public BuildStatusRenderer(BatchOutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, TimeProvider timeProvider) {
        this(listener, buildStatusLabel, console, consoleMetaData, timeProvider, Executors.newSingleThreadScheduledExecutor());
    }

    BuildStatusRenderer(BatchOutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, TimeProvider timeProvider, ScheduledExecutorService executor) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.timeProvider = timeProvider;
        this.executor = executor;
        this.buildStartTimestamp = timeProvider.getCurrentTime();
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof PhaseProgressStartEvent) {
            onPhaseStart((PhaseProgressStartEvent) event);
        } else if (event instanceof ProgressStartEvent) {
            onStart((ProgressStartEvent) event);
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            if (completeEvent.getProgressOperationId().equals(rootOperationId)) {
                rootOperationId = null;
            } else if (completeEvent.getProgressOperationId().equals(currentPhaseProgressOperationId)) {
                startedPhaseChildren.clear();
            } else if (startedPhaseChildren.contains(completeEvent.getProgressOperationId())) {
                onPhaseProgress();
            }
        } else if (event instanceof EndOutputEvent) {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
            executor.shutdown();
        }
    }

    @Override
    public void onOutput(Iterable<OutputEvent> events) {
        synchronized (lock) {
            super.onOutput(events);
            listener.onOutput(events);
            renderNow(timeProvider.getCurrentTime());
        }
    }

    private String trimToConsole(String str) {
        int width = consoleMetaData.getCols() - 1;
        if (width > 0 && width < str.length()) {
            return str.substring(0, width);
        }
        return str;
    }

    private void renderNow(long now) {
        if (currentBuildStatus != null && !currentBuildStatus.isEmpty()) {
            if ((future == null || future.isCancelled()) && !executor.isShutdown()) {
                future = executor.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            renderNow(timeProvider.getCurrentTime());
                        }
                    }
                }, RENDER_NOW_PERIOD_MILLISECONDS, RENDER_NOW_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
            }
            String elapsedTime = elapsedTimeFormatter.format(now - buildStartTimestamp);
            buildStatusLabel.setText(Arrays.asList(
                new Span(Style.of(Style.Emphasis.BOLD), trimToConsole(format(currentBuildStatus, elapsedTime)))));
        }
        console.flush();
    }

    private static String format(String status, String elapsedTime) {
        return status + " [" + elapsedTime + "]";
    }

    private void onPhaseStart(PhaseProgressStartEvent event) {
        startedPhaseChildren.clear();
        progressBarFormatter = newProgressBar(event.getDescription(), event.getChildren());
        currentBuildStatus = progressBarFormatter.getProgress();
        currentPhaseProgressOperationId = event.getProgressOperationId();
    }

    private void onPhaseProgress() {
        currentBuildStatus = progressBarFormatter.incrementAndGetProgress();
    }

    private void onStart(ProgressStartEvent startEvent) {
        if (startEvent.getBuildOperationId() != null && ((OperationIdentifier) startEvent.getBuildOperationId()).getId() == 0L) {
            // if root operation, assign root operation and initialize display
            rootOperationId = startEvent.getProgressOperationId();
            progressBarFormatter = newProgressBar("INITIALIZING", 1);
            currentBuildStatus = progressBarFormatter.getProgress();
        } else if (startEvent.getParentProgressOperationId() != null && startEvent.getParentProgressOperationId().equals(currentPhaseProgressOperationId)) {
            startedPhaseChildren.add(startEvent.getProgressOperationId());
        }
    }

    @VisibleForTesting
    public ProgressBarFormatter newProgressBar(String initialSuffix, long totalWorkItems) {
        return new ProgressBarFormatter(PROGRESS_BAR_PREFIX,
            PROGRESS_BAR_WIDTH,
            PROGRESS_BAR_SUFFIX,
            PROGRESS_BAR_COMPLETE_CHAR,
            PROGRESS_BAR_INCOMPLETE_CHAR,
            initialSuffix,
            totalWorkItems);
    }
}
