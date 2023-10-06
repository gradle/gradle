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
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;

import java.util.HashSet;
import java.util.Set;

abstract class BuildStatusRenderer implements OutputEventListener {

    protected enum Phase {
        Initializing, Configuring, Executing, Waiting
    }

    private final OutputEventListener listener;
    private OperationIdentifier buildProgressOperationId;
    private Phase currentPhase;
    private final Set<OperationIdentifier> currentPhaseChildren = new HashSet<OperationIdentifier>();
    private long currentTimePeriod;

    // Used to maintain timer
    private long buildStartTimestamp;
    private boolean timerEnabled;

    BuildStatusRenderer(OutputEventListener listener) {
        this.listener = listener;
    }

    protected Phase getCurrentPhase() {
        return currentPhase;
    }

    protected long getBuildStartTimestamp() {
        return buildStartTimestamp;
    }

    protected boolean getTimerEnabled() {
        return timerEnabled;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            if (startEvent.isBuildOperationStart()) {
                if (buildStartTimestamp == 0 && startEvent.getParentProgressOperationId() == null) {
                    // The very first event starts the Initializing phase
                    // TODO - should use BuildRequestMetaData to determine the build start time
                    buildStartTimestamp = startEvent.getTimestamp();
                    buildProgressOperationId = startEvent.getProgressOperationId();
                    phaseStartedInternal(startEvent, Phase.Initializing);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_ROOT_BUILD) {
                    // Once the root build starts configuring, we are in Configuring phase
                    phaseStartedInternal(startEvent, Phase.Configuring);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_BUILD && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    phaseHasMoreProgress(startEvent);
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CONFIGURE_PROJECT && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId());
                } else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.RUN_MAIN_TASKS) {
                    phaseStartedInternal(startEvent, Phase.Executing);
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
                buildEndedInternal();
            } else if (currentPhaseChildren.remove(completeEvent.getProgressOperationId())) {
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

    protected abstract void renderNow(long now);

    private void phaseStartedInternal(ProgressStartEvent startEvent, Phase phase) {
        timerEnabled = true;
        currentPhase = phase;
        currentPhaseChildren.clear();
        phaseStarted(startEvent, phase);
    }

    protected abstract void phaseStarted(ProgressStartEvent startEvent, Phase phase);

    protected abstract void phaseHasMoreProgress(ProgressStartEvent progressStartEvent);

    protected abstract void phaseProgressed(ProgressCompleteEvent progressEvent);

    private void buildEndedInternal() {
        currentPhase = Phase.Waiting;
        buildProgressOperationId = null;
        currentPhaseChildren.clear();
        timerEnabled = false;
        buildEnded();
    }

    protected abstract void buildEnded();
}
