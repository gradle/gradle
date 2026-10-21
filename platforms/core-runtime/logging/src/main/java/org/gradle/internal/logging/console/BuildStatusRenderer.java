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
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * <p>This listener displays nothing unless it receives periodic {@link UpdateNowEvent} clock events.</p>
 */
public class BuildStatusRenderer implements OutputEventListener {

    private enum Phase {
        Initializing, Configuring, Executing
    }

    private final OutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private final @Nullable ProgramStatusReporter programStatusReporter;
    private @Nullable OperationIdentifier buildProgressOperationId;
    private @Nullable ProgressStartEvent nextBuildCandidate;
    private boolean firstBuild = true;
    private @Nullable Phase currentPhase;
    private final Set<OperationIdentifier> currentPhaseChildren = new HashSet<>();
    private long currentTimePeriod;

    // What actually shows up on the console
    private @Nullable ProgressBar progressBar;

    // Used to maintain timer
    private long buildStartTimestamp;
    private final TersePrettyDurationFormatter durationFormatter = new TersePrettyDurationFormatter();
    private boolean timerEnabled;

    public BuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData) {
        this(listener, buildStatusLabel, console, consoleMetaData, null);
    }

    public BuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData, @Nullable ProgramStatusReporter programStatusReporter) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
        this.programStatusReporter = programStatusReporter;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.isBuildOperationStart()) {
                if (buildStartTimestamp == 0 && startEvent.getParentProgressOperationId() == null) {
                    // The very first event starts the Initializing phase
                    // TODO - should use BuildRequestMetaData to determine the build start time
                    buildStarted(startEvent);
                    phaseStarted(startEvent, Phase.Initializing);
                } else if (buildProgressOperationId == null && startEvent.getParentProgressOperationId() == null) {
                    // After a build has ended, a root operation is either the next build of a continuous build or other work, such as cache cleanup.
                    // It is taken as the next build once the root build starts configuring or running tasks.
                    nextBuildCandidate = startEvent;
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_ROOT_BUILD) {
                    // Once the root build starts configuring, we are in Configuring phase
                    startNextBuildIfAny();
                    phaseStarted(startEvent, Phase.Configuring);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_BUILD && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    phaseHasMoreProgress(startEvent);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_PROJECT && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId());
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_MAIN_TASKS) {
                    startNextBuildIfAny();
                    phaseStarted(startEvent, Phase.Executing);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_WORK && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    phaseHasMoreProgress(startEvent);
                } else if (startEvent.getBuildOperationCategory().isTopLevelWorkItem() && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId());
                }
            }
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            if (completeEvent.getProgressOperationId().equals(buildProgressOperationId)) {
                buildEnded(completeEvent);
            } else if (nextBuildCandidate != null && completeEvent.getProgressOperationId().equals(nextBuildCandidate.getProgressOperationId())) {
                nextBuildCandidate = null;
            } else if (currentPhaseChildren.remove(completeEvent.getProgressOperationId())) {
                phaseProgressed(completeEvent);
            }
        }

        if (event instanceof ProgressStartEvent || event instanceof ProgressCompleteEvent) {
            updateProgramStatus();
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
            buildStatusLabel.setText(progressBar.formatProgress(timerEnabled, now - buildStartTimestamp));
        }
        console.flush();
        if (programStatusReporter != null) {
            // Written after the console flush, so the sequence cannot land inside another escape sequence
            programStatusReporter.flush();
        }
    }

    private void updateProgramStatus() {
        if (programStatusReporter == null || currentPhase == null) {
            return;
        }
        // The percentage restarts for each phase, so it is reported only for the Executing phase
        Integer progress = currentPhase == Phase.Executing && progressBar != null ? progressBar.getProgressPercent() : null;
        programStatusReporter.updateWorking(progress);
    }

    private void buildStarted(ProgressStartEvent rootBuildOperationStart) {
        buildStartTimestamp = rootBuildOperationStart.getTimestamp();
        buildProgressOperationId = rootBuildOperationStart.getProgressOperationId();
        nextBuildCandidate = null;
    }

    private void startNextBuildIfAny() {
        if (buildProgressOperationId == null && nextBuildCandidate != null) {
            buildStarted(nextBuildCandidate);
        }
    }

    private void phaseStarted(ProgressStartEvent progressStartEvent, Phase phase) {
        timerEnabled = true;
        currentPhase = phase;
        currentPhaseChildren.clear();
        int totalProgress = progressStartEvent.getTotalProgress();
        progressBar = ProgressBar.createProgressBar(consoleMetaData, phase.name().toUpperCase(Locale.ROOT), totalProgress);
    }

    private void phaseHasMoreProgress(ProgressStartEvent progressStartEvent) {
        if (progressBar != null) {
            progressBar.moreProgress(progressStartEvent.getTotalProgress());
        }
    }

    private void phaseProgressed(ProgressCompleteEvent progressEvent) {
        if (progressBar != null) {
            progressBar.update(progressEvent.isFailed());
        }
    }

    private void buildEnded(ProgressCompleteEvent rootBuildOperationComplete) {
        if (programStatusReporter != null) {
            boolean failed = rootBuildOperationComplete.isFailed();
            // Same text as printed on the console at the end of the build, where the first build is measured from the start of the client JVM
            long startTime = firstBuild ? Math.min(programStatusReporter.getClientStartTime(), buildStartTimestamp) : buildStartTimestamp;
            String duration = durationFormatter.format(rootBuildOperationComplete.getTimestamp() - startTime);
            programStatusReporter.updateOutcome(failed, "BUILD " + (failed ? "FAILED" : "SUCCESSFUL") + " in " + duration);
        }
        firstBuild = false;
        progressBar = ProgressBar.createProgressBar(consoleMetaData, "WAITING", 1);
        currentPhase = null;
        buildProgressOperationId = null;
        currentPhaseChildren.clear();
        timerEnabled = false;
    }
}
