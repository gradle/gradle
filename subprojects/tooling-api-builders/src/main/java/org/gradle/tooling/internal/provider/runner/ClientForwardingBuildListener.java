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

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.provider.events.AbstractBuildResult;
import org.gradle.tooling.internal.provider.events.DefaultBuildDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultBuildFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultBuildFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultBuildStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultBuildSuccessResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildListener implements BuildListener {

    private final BuildEventConsumer eventConsumer;
    private final EventTracker eventTracker = new EventTracker();

    ClientForwardingBuildListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    /**
     * <p>Called when the build is started.</p>
     *
     * @param gradle The build which is being started. Never null.
     */
    @Override
    public void buildStarted(Gradle gradle) {
        eventConsumer.dispatch(new DefaultBuildStartedProgressEvent(eventTracker.eventTime(), adapt(gradle)));
    }

    /**
     * <p>Called when the build settings have been loaded and evaluated. The settings object is fully configured and is ready to use to load the build projects.</p>
     *
     * @param settings The settings. Never null.
     */
    @Override
    public void settingsEvaluated(Settings settings) {
        DefaultBuildStartedProgressEvent startEvent = new DefaultBuildStartedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(settings), "settings evaluation", EventIdGenerator.generateId(settings.getGradle()))
        );
        DefaultBuildFinishedProgressEvent endEvent = new DefaultBuildFinishedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(settings), "settings evaluation", EventIdGenerator.generateId(settings.getGradle())),
            new DefaultBuildSuccessResult(eventTracker.buildStartTime(), eventTracker.eventTime())
        );
        eventConsumer.dispatch(startEvent);
        eventConsumer.dispatch(endEvent);
    }

    /**
     * <p>Called when the projects for the build have been created from the settings. None of the projects have been evaluated.</p>
     *
     * @param gradle The build which has been loaded. Never null.
     */
    @Override
    public void projectsLoaded(Gradle gradle) {
        DefaultBuildStartedProgressEvent startEvent = new DefaultBuildStartedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(gradle, "projectsLoaded"), "projects loading", EventIdGenerator.generateId(gradle))
        );
        DefaultBuildFinishedProgressEvent endEvent = new DefaultBuildFinishedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(gradle, "projectsLoaded"), "projects loading", EventIdGenerator.generateId(gradle)),
            new DefaultBuildSuccessResult(eventTracker.buildStartTime(), eventTracker.eventTime())
        );
        eventConsumer.dispatch(startEvent);
        eventConsumer.dispatch(endEvent);
    }

    /**
     * <p>Called when all projects for the build have been evaluated. The project objects are fully configured and are ready to use to populate the task graph.</p>
     *
     * @param gradle The build which has been evaluated. Never null.
     */
    @Override
    public void projectsEvaluated(Gradle gradle) {
        DefaultBuildStartedProgressEvent startEvent = new DefaultBuildStartedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(gradle, "projectsEvaluated"), "projects evaluation", EventIdGenerator.generateId(gradle))
        );
        DefaultBuildFinishedProgressEvent endEvent = new DefaultBuildFinishedProgressEvent(
            eventTracker.eventTime(),
            new DefaultBuildDescriptor(EventIdGenerator.generateId(gradle, "projectsEvaluated"), "projects evaluation", EventIdGenerator.generateId(gradle)),
            new DefaultBuildSuccessResult(eventTracker.buildStartTime(), eventTracker.eventTime())
        );
        eventConsumer.dispatch(startEvent);
        eventConsumer.dispatch(endEvent);
    }

    /**
     * <p>Called when the build is completed. All selected tasks have been executed.</p>
     *
     * @param result The result of the build. Never null.
     */
    @Override
    public void buildFinished(BuildResult result) {
        Gradle gradle = result.getGradle();
        eventConsumer.dispatch(new DefaultBuildFinishedProgressEvent(
            eventTracker.eventTime(),
            adapt(gradle),
            adaptResult(result)));
    }

    private AbstractBuildResult adaptResult(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure != null) {
            return new DefaultBuildFailureResult(eventTracker.buildStartTime(), eventTracker.eventTime(), DefaultFailure.fromThrowable(failure));
        }
        return new DefaultBuildSuccessResult(eventTracker.buildStartTime(), eventTracker.eventTime());
    }

    private static DefaultBuildDescriptor adapt(Gradle gradle) {
        if (gradle == null) {
            return null;
        }
        return new DefaultBuildDescriptor(EventIdGenerator.generateId(gradle), "Gradle " + gradle.getGradleVersion(), adapt(gradle.getParent()));
    }

    /**
     * Used to track the event times. Separated from the listener itself for consistency and ease of replacement.
     */
    private static class EventTracker {
        private long buildStartTime = Long.MIN_VALUE;

        public long buildStartTime() {
            if (buildStartTime == Long.MIN_VALUE) {
                buildStartTime = System.currentTimeMillis();
            }
            return buildStartTime;
        }

        public long eventTime() {
            return System.currentTimeMillis();
        }
    }
}
