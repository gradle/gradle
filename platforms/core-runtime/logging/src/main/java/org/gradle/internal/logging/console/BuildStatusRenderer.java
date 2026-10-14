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

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * <p>This listener displays nothing unless it receives periodic {@link UpdateNowEvent} clock events.</p>
 */
public class BuildStatusRenderer implements OutputEventListener {

    /**
     * System property set by the launcher for {@code --simulate-dv-off}, which advertises Develocity next to the estimated time remaining.
     */
    public static final String SIMULATE_DEVELOCITY_OFF_PROPERTY = "org.gradle.internal.console.simulate-dv-off";
    private static final String SIMULATED_DEVELOCITY_OFF_NOTE = " - for more accurate timing info BUY DEVELOCITY NOW!!!";

    private enum Phase {
        Initializing, Configuring, Executing
    }

    private final OutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private @Nullable OperationIdentifier buildProgressOperationId;
    private @Nullable Phase currentPhase;
    private final Set<OperationIdentifier> currentPhaseChildren = new HashSet<>();
    private long currentTimePeriod;

    // What actually shows up on the console
    private @Nullable ProgressBar progressBar;

    // Used to maintain timer
    private long buildStartTimestamp;
    private boolean timerEnabled;

    // Used to estimate time remaining in the execution phase
    private final RemainingTimeEstimator remainingTimeEstimator;
    // Parent of each running operation, used to find the operation that scheduled a work item, and the work item that started a nested build
    private final Map<OperationIdentifier, OperationIdentifier> runningOperationParents = new HashMap<>();
    // Operations that scheduled work in the execution phase
    private final Set<OperationIdentifier> schedulingOperations = new HashSet<>();

    public BuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, WorkDurationEstimator workDurationEstimator) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.remainingTimeEstimator = new RemainingTimeEstimator(workDurationEstimator);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.isBuildOperationStart()) {
                if (startEvent.getParentProgressOperationId() != null) {
                    runningOperationParents.put(startEvent.getProgressOperationId(), startEvent.getParentProgressOperationId());
                }
                if (buildStartTimestamp == 0 && startEvent.getParentProgressOperationId() == null) {
                    // The very first event starts the Initializing phase
                    // TODO - should use BuildRequestMetaData to determine the build start time
                    buildStartTimestamp = startEvent.getTimestamp();
                    buildProgressOperationId = startEvent.getProgressOperationId();
                    phaseStarted(startEvent, Phase.Initializing);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_ROOT_BUILD) {
                    // Once the root build starts configuring, we are in Configuring phase
                    phaseStarted(startEvent, Phase.Configuring);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_BUILD && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    phaseHasMoreProgress(startEvent);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_PROJECT && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId());
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_MAIN_TASKS) {
                    phaseStarted(startEvent, Phase.Executing);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_WORK && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    // A work item that runs a nested build, such as a GradleBuild task, waits on the nested build's work
                    schedulingOperations.add(startEvent.getProgressOperationId());
                    remainingTimeEstimator.workScheduled(startEvent, findAncestor(startEvent, currentPhaseChildren::contains));
                    phaseHasMoreProgress(startEvent);
                } else if (startEvent.getBuildOperationCategory().isTopLevelWorkItem() && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId());
                    remainingTimeEstimator.workStarted(startEvent, findAncestor(startEvent, schedulingOperations::contains));
                }
            }
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            runningOperationParents.remove(completeEvent.getProgressOperationId());
            if (completeEvent.getProgressOperationId().equals(buildProgressOperationId)) {
                buildEnded();
            } else if (currentPhaseChildren.remove(completeEvent.getProgressOperationId())) {
                remainingTimeEstimator.workCompleted(completeEvent.getProgressOperationId(), completeEvent.getTimestamp());
                phaseProgressed(completeEvent);
            }
        }

        listener.onOutput(event);

        if (event instanceof UpdateNowEvent) {
            currentTimePeriod = ((UpdateNowEvent) event).getTimestamp();
            renderNow(currentTimePeriod);
        } else if (event instanceof EndOutputEvent || event instanceof FlushOutputEvent) {
            renderNow(currentTimePeriod);
        }
    }

    private void renderNow(long now) {
        if (progressBar != null) {
            long elapsedTime = now - buildStartTimestamp;
            buildStatusLabel.setText(currentPhase == Phase.Executing
                ? progressBar.formatProgress(timerEnabled, elapsedTime, remainingTimeEstimator.estimateRemainingMillis(now), estimateNote())
                : progressBar.formatProgress(timerEnabled, elapsedTime));
        }
        console.flush();
    }

    private static String estimateNote() {
        return Boolean.getBoolean(SIMULATE_DEVELOCITY_OFF_PROPERTY) ? SIMULATED_DEVELOCITY_OFF_NOTE : "";
    }

    private void phaseStarted(ProgressStartEvent progressStartEvent, Phase phase) {
        timerEnabled = true;
        currentPhase = phase;
        currentPhaseChildren.clear();
        remainingTimeEstimator.reset();
        schedulingOperations.clear();
        if (phase == Phase.Executing) {
            schedulingOperations.add(progressStartEvent.getProgressOperationId());
            remainingTimeEstimator.workScheduled(progressStartEvent, null);
        }
        int totalProgress = progressStartEvent.getTotalProgress();
        progressBar = ProgressBar.createProgressBar(consoleMetaData, phase.name().toUpperCase(Locale.ROOT), totalProgress);
    }

    private void phaseHasMoreProgress(ProgressStartEvent progressStartEvent) {
        if (progressBar != null) {
            progressBar.moreProgress(progressStartEvent.getTotalProgress());
        }
    }

    /**
     * Returns the nearest running ancestor operation of the given operation that matches, or null when there is none.
     */
    @Nullable
    private OperationIdentifier findAncestor(ProgressStartEvent startEvent, Predicate<OperationIdentifier> matches) {
        OperationIdentifier ancestor = startEvent.getParentProgressOperationId();
        while (ancestor != null) {
            if (matches.test(ancestor)) {
                return ancestor;
            }
            ancestor = runningOperationParents.get(ancestor);
        }
        return null;
    }

    private void phaseProgressed(ProgressCompleteEvent progressEvent) {
        if (progressBar != null) {
            progressBar.update(progressEvent.isFailed());
        }
    }

    private void buildEnded() {
        progressBar = ProgressBar.createProgressBar(consoleMetaData, "WAITING", 1);
        currentPhase = null;
        buildProgressOperationId = null;
        currentPhaseChildren.clear();
        runningOperationParents.clear();
        schedulingOperations.clear();
        remainingTimeEstimator.reset();
        timerEnabled = false;
    }
}
