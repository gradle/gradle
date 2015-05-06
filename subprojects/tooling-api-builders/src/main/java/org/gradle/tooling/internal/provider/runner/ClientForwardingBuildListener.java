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
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.progress.InternalBuildListener;
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

    private AbstractBuildResult adaptResult(BuildResult result, long startTime, long endTime) {
        Throwable failure = result.getFailure();
        if (failure != null) {
            return new DefaultBuildFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
        }
        return new DefaultBuildSuccessResult(startTime, endTime);
    }

    @Override
    public void started(Object source, long startTime, String eventType) {
        DefaultBuildDescriptor descriptor = createDescriptor(source, eventType, eventType + " started");
        DefaultBuildStartedProgressEvent startEvent = new DefaultBuildStartedProgressEvent(
            startTime,
            descriptor
        );
        eventConsumer.dispatch(startEvent);
    }

    @Override
    public void finished(Object source, long startTime, long endTime, String eventType) {
        DefaultBuildDescriptor descriptor = createDescriptor(source, eventType, eventType + " finished");
        DefaultBuildFinishedProgressEvent finishEvent = new DefaultBuildFinishedProgressEvent(
            endTime,
            descriptor,
            adaptResult(source, startTime, endTime)
        );
        eventConsumer.dispatch(finishEvent);
    }

    private DefaultBuildDescriptor createDescriptor(Object source, String eventType, String displayName) {
        DefaultBuildDescriptor descriptor = null;
        if (source instanceof BuildResult) {
            return createDescriptor(((BuildResult) source).getGradle(), eventType, displayName);
        }
        if (source instanceof Gradle) {
            descriptor = createGradleDescriptor((Gradle) source, eventType, displayName);
        }
        return descriptor;
    }

    private DefaultBuildDescriptor createGradleDescriptor(Gradle source, String eventType, String displayName) {
        Object id = InternalBuildListener.BUILD_TYPE.equals(eventType)?EventIdGenerator.generateId(source):EventIdGenerator.generateId(source, eventType);
        Object parentId = InternalBuildListener.BUILD_TYPE.equals(eventType)?EventIdGenerator.generateId(source.getParent()):EventIdGenerator.generateId(source);
        return new DefaultBuildDescriptor(id, eventType, displayName, parentId);
    }

    private AbstractBuildResult adaptResult(Object result, long startTime, long endTime) {
        if (result instanceof BuildResult) {
            return adaptResult((BuildResult)result, startTime, endTime);
        }
        return new DefaultBuildSuccessResult(startTime, endTime);
    }

}
