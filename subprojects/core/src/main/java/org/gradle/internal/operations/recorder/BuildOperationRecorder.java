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

package org.gradle.internal.operations.recorder;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;

import java.util.ArrayList;
import java.util.List;

public class BuildOperationRecorder implements Stoppable {

    private final BuildOperationListenerManager listenerManager;
    private final BuildOperationListener listener;

    private List<RecordedBuildOperation> recordedEvents = new ArrayList<RecordedBuildOperation>();

    public BuildOperationRecorder(BuildOperationListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        this.listener = new BuildOperationListener() {
            @Override
            public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                recordedEvents.add(new RecordedBuildOperation(buildOperation, startEvent));
            }

            @Override
            public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                recordedEvents.add(new RecordedBuildOperation(buildOperation, finishEvent));
            }
        };
        this.listenerManager.addListener(listener);
    }

    public List<RecordedBuildOperation> getRecordedEvents() {
        List<RecordedBuildOperation> returnEvents = recordedEvents;
        recordedEvents = new ArrayList<RecordedBuildOperation>();
        return returnEvents;
    }

    @Override
    public void stop() {
        listenerManager.removeListener(listener);
    }

    public static class RecordedBuildOperation {
        public final BuildOperationDescriptor buildOperation;
        public final Object event;

        public RecordedBuildOperation(BuildOperationDescriptor buildOperation, Object event) {
            this.buildOperation = buildOperation;
            this.event = event;
        }
    }
}
