/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.BuildResult;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.InternalBuildOperation;
import org.gradle.tooling.internal.provider.events.*;

import java.util.Collections;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildListener implements InternalBuildListener {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingBuildListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    private AbstractBuildOperationResult adaptResult(BuildResult result, long startTime, long endTime) {
        Throwable failure = result.getFailure();
        if (failure != null) {
            return new DefaultBuildOperationFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
        }
        return new DefaultBuildOperationSuccessResult(startTime, endTime);
    }

    @Override
    public void started(InternalBuildOperation source, long startTime, String eventType) {
        DefaultBuildDescriptor descriptor = createDescriptor(source, eventType, eventType + " started");
        DefaultBuildOperationStartedProgressEvent startEvent = new DefaultBuildOperationStartedProgressEvent(
            startTime,
            descriptor
        );
        eventConsumer.dispatch(startEvent);
    }

    @Override
    public void finished(InternalBuildOperation source, long startTime, long endTime, String eventType) {
        DefaultBuildDescriptor descriptor = createDescriptor(source, eventType, eventType + " finished");
        DefaultBuildOperationFinishedProgressEvent finishEvent = new DefaultBuildOperationFinishedProgressEvent(
            endTime,
            descriptor,
            adaptResult(source.getPayload(), startTime, endTime)
        );
        eventConsumer.dispatch(finishEvent);
    }

    private DefaultBuildDescriptor createDescriptor(InternalBuildOperation source, String eventType, String displayName) {
        return new DefaultBuildDescriptor(source.getId(), eventType, displayName, source.getParentId());
    }

    private AbstractBuildOperationResult adaptResult(Object result, long startTime, long endTime) {
        if (result instanceof Throwable) {
            return adaptResult((Throwable) result, startTime, endTime);
        }
        if (result instanceof BuildResult) {
            return adaptResult((BuildResult) result, startTime, endTime);
        }
        return new DefaultBuildOperationSuccessResult(startTime, endTime);
    }

    private DefaultBuildOperationFailureResult adaptResult(Throwable error, long startTime, long endTime) {
        return new DefaultBuildOperationFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(error)));
    }

}
