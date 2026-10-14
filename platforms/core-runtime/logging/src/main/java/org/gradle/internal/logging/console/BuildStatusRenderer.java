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
import org.gradle.internal.logging.events.WorkItemProgressEvent;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shows one progress bar for the whole build, with the name of the current phase and the running and queued counts next to it.
 *
 * <p>The bar counts the projects to configure and the work items to run, in all builds of the build tree.
 * The total grows when Gradle finds more work, so the bar can move backwards.
 * Each build, also each build of a continuous build, starts a new bar.</p>
 *
 * <p>This listener displays nothing unless it receives periodic {@link UpdateNowEvent} clock events.</p>
 */
public class BuildStatusRenderer implements OutputEventListener {

    private enum Phase {
        Initializing, Loading, Configuring, Executing
    }

    private final OutputEventListener listener;
    private final StyledLabel buildStatusLabel;
    private final Console console;
    private final ConsoleMetaData consoleMetaData;
    private @Nullable OperationIdentifier buildProgressOperationId;
    private @Nullable OperationIdentifier loadProgressOperationId;
    private @Nullable Phase currentPhase;
    private final Set<OperationIdentifier> configuringProjects = new HashSet<>();
    private final Set<OperationIdentifier> runningWorkItems = new HashSet<>();
    private int expectedProjects;
    private int configuredProjects;
    private int pendingWorkItems;
    private int startedWorkItems;
    private long currentTimePeriod;

    // What actually shows up on the console
    private @Nullable ProgressBar progressBar;

    // Used to maintain timer
    private long buildStartTimestamp;
    private boolean timerEnabled;

    public BuildStatusRenderer(OutputEventListener listener, StyledLabel buildStatusLabel, Console console, ConsoleMetaData consoleMetaData) {
        this.listener = listener;
        this.buildStatusLabel = buildStatusLabel;
        this.console = console;
        this.consoleMetaData = consoleMetaData;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.isBuildOperationStart()) {
                if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_BUILD) {
                    // Each build starts a new bar, also each build of a continuous build
                    // TODO - should use BuildRequestMetaData to determine the build start time
                    buildStartTimestamp = startEvent.getTimestamp();
                    buildProgressOperationId = startEvent.getProgressOperationId();
                    currentPhase = null;
                    phaseStarted(startEvent, Phase.Initializing);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_ROOT_BUILD) {
                    // Once the root build starts configuring, we are in Configuring phase
                    phaseStarted(startEvent, Phase.Configuring);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.LOAD_CONFIGURATION_CACHE && currentPhase == Phase.Initializing) {
                    // Reusing the configuration cache takes the place of configuring the build
                    phaseStarted(startEvent, Phase.Loading);
                    workStarted(startEvent);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_MAIN_TASKS) {
                    phaseStarted(startEvent, Phase.Executing);
                } else if (currentPhase != null) {
                    workStarted(startEvent);
                }
            }
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            if (completeEvent.getProgressOperationId().equals(loadProgressOperationId)) {
                loadProgressOperationId = null;
            }
            if (completeEvent.getProgressOperationId().equals(buildProgressOperationId)) {
                buildEnded();
            } else if (configuringProjects.remove(completeEvent.getProgressOperationId())) {
                projectConfigured(completeEvent);
            } else if (runningWorkItems.remove(completeEvent.getProgressOperationId())) {
                workDone(completeEvent);
            }
        } else if (event instanceof WorkItemProgressEvent) {
            workItemProgressed((WorkItemProgressEvent) event);
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
            progressBar.setRunning(configuringProjects.size() + runningWorkItems.size() + startedWorkItems);
            buildStatusLabel.setText(progressBar.formatProgress(timerEnabled, now - buildStartTimestamp));
        }
        console.flush();
    }

    private void phaseStarted(ProgressStartEvent startEvent, Phase phase) {
        timerEnabled = true;
        String phaseLabel = phase.name().toUpperCase(Locale.ROOT);
        if (currentPhase == null || progressBar == null) {
            resetCounts();
            progressBar = ProgressBar.createProgressBar(consoleMetaData, phaseLabel, 0);
        } else {
            progressBar.setPhase(phaseLabel);
        }
        // Until tasks run, the work to do is not known yet
        progressBar.setEdge(phase == Phase.Executing ? ProgressBar.Edge.STEADY : ProgressBar.Edge.PULSING);
        currentPhase = phase;
        if (phase == Phase.Configuring) {
            projectsDiscovered(startEvent);
        } else {
            if (phase == Phase.Executing) {
                dropProjectsNotConfigured();
            }
            addToTotal(startEvent.getTotalProgress());
        }
    }

    private void workStarted(ProgressStartEvent startEvent) {
        BuildOperationCategory category = startEvent.getBuildOperationCategory();
        if (category == BuildOperationCategory.CONFIGURE_BUILD) {
            projectsDiscovered(startEvent);
        } else if (category == BuildOperationCategory.CONFIGURE_PROJECT) {
            configuringProjects.add(startEvent.getProgressOperationId());
        } else if (category == BuildOperationCategory.RUN_WORK) {
            addToTotal(startEvent.getTotalProgress());
        } else if (category.isTopLevelWorkItem()) {
            runningWorkItems.add(startEvent.getProgressOperationId());
        } else if (category == BuildOperationCategory.LOAD_CONFIGURATION_CACHE) {
            loadProgressOperationId = startEvent.getProgressOperationId();
            stepStarted(startEvent);
        } else if (category == BuildOperationCategory.STORE_CONFIGURATION_CACHE) {
            stepStarted(startEvent);
        } else if (category == BuildOperationCategory.BUILD_WORK_GRAPH && loadProgressOperationId == null) {
            // Loading the configuration cache also builds the work graph, which counts as part of the load
            stepStarted(startEvent);
        }
    }

    // Building the work graph and storing or loading the configuration cache each count as one unit of work
    private void stepStarted(ProgressStartEvent startEvent) {
        addToTotal(1);
        runningWorkItems.add(startEvent.getProgressOperationId());
    }

    private void projectsDiscovered(ProgressStartEvent startEvent) {
        expectedProjects += startEvent.getTotalProgress();
        addToTotal(startEvent.getTotalProgress());
    }

    private void projectConfigured(ProgressCompleteEvent completeEvent) {
        configuredProjects++;
        if (configuredProjects > expectedProjects) {
            expectedProjects = configuredProjects;
            addToTotal(1);
        }
        workDone(completeEvent);
    }

    // Some projects are never configured, for example with configure-on-demand
    private void dropProjectsNotConfigured() {
        int notConfigured = expectedProjects - configuredProjects;
        if (notConfigured > 0) {
            expectedProjects = configuredProjects;
            addToTotal(-notConfigured);
        }
    }

    private void addToTotal(int count) {
        if (progressBar != null) {
            progressBar.moreProgress(count);
        }
    }

    private void workDone(ProgressCompleteEvent completeEvent) {
        if (progressBar != null) {
            progressBar.update(completeEvent.isFailed());
        }
    }

    private void workItemProgressed(WorkItemProgressEvent event) {
        if (currentPhase == null || progressBar == null) {
            return;
        }
        if (event.getKind() == WorkItemProgressEvent.Kind.SUBMITTED) {
            pendingWorkItems += event.getCount();
            addToTotal(event.getCount());
        } else if (event.getKind() == WorkItemProgressEvent.Kind.STARTED) {
            startedWorkItems = Math.min(startedWorkItems + event.getCount(), pendingWorkItems);
        } else {
            // Items submitted before this build started were never added to the total
            int completed = Math.min(event.getCount(), pendingWorkItems);
            pendingWorkItems -= completed;
            // Events only carry counts, so assume the completed items are the started ones
            startedWorkItems = Math.max(0, startedWorkItems - completed);
            boolean failed = event.getKind() == WorkItemProgressEvent.Kind.FAILED;
            for (int i = 0; i < completed; i++) {
                progressBar.update(failed);
            }
        }
    }

    private void resetCounts() {
        configuringProjects.clear();
        runningWorkItems.clear();
        expectedProjects = 0;
        configuredProjects = 0;
        pendingWorkItems = 0;
        startedWorkItems = 0;
        loadProgressOperationId = null;
    }

    private void buildEnded() {
        progressBar = ProgressBar.createProgressBar(consoleMetaData, "WAITING", 0);
        progressBar.setEdge(ProgressBar.Edge.NONE);
        currentPhase = null;
        buildProgressOperationId = null;
        resetCounts();
        timerEnabled = false;
    }
}
