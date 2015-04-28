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
import org.gradle.tooling.internal.provider.events.AbstractBuildAdvanceResult;
import org.gradle.tooling.internal.provider.events.DefaultBuildAdvanceProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultBuildDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultBuildFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultBuildFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultBuildStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultBuildSuccessResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultProjectsEvaluatedBuildAdvanceResult;
import org.gradle.tooling.internal.provider.events.DefaultProjectsLoadedBuildAdvanceResult;
import org.gradle.tooling.internal.provider.events.DefaultSettingsEvaluatedBuildAdvanceResult;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildListener implements BuildListener {

    private final BuildEventConsumer eventConsumer;

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
        eventConsumer.dispatch(new DefaultBuildStartedProgressEvent(System.currentTimeMillis(), adapt(gradle)));
    }

    /**
     * <p>Called when the build settings have been loaded and evaluated. The settings object is fully configured and is ready to use to load the build projects.</p>
     *
     * @param settings The settings. Never null.
     */
    @Override
    public void settingsEvaluated(Settings settings) {
        eventConsumer.dispatch(new DefaultBuildAdvanceProgressEvent(System.currentTimeMillis(), adapt(settings.getGradle()), adaptSettings(settings)));
    }

    private DefaultSettingsEvaluatedBuildAdvanceResult adaptSettings(Settings settings) {
        return new DefaultSettingsEvaluatedBuildAdvanceResult();
    }

    /**
     * <p>Called when the projects for the build have been created from the settings. None of the projects have been evaluated.</p>
     *
     * @param gradle The build which has been loaded. Never null.
     */
    @Override
    public void projectsLoaded(Gradle gradle) {
        eventConsumer.dispatch(new DefaultBuildAdvanceProgressEvent(
                System.currentTimeMillis(),
                adapt(gradle),
                new DefaultProjectsLoadedBuildAdvanceResult()));
    }

    /**
     * <p>Called when all projects for the build have been evaluated. The project objects are fully configured and are ready to use to populate the task graph.</p>
     *
     * @param gradle The build which has been evaluated. Never null.
     */
    @Override
    public void projectsEvaluated(Gradle gradle) {
        eventConsumer.dispatch(new DefaultBuildAdvanceProgressEvent(
                System.currentTimeMillis(),
                adapt(gradle),
                new DefaultProjectsEvaluatedBuildAdvanceResult()));
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
                System.currentTimeMillis(),
                adapt(gradle),
                adaptResult(result)));
    }

    private static AbstractBuildAdvanceResult adaptResult(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure != null) {
            return new DefaultBuildFailureResult(DefaultFailure.fromThrowable(failure));
        }
        return new DefaultBuildSuccessResult();
    }

    private static DefaultBuildDescriptor adapt(Gradle gradle) {
        if (gradle == null) {
            return null;
        }
        return new DefaultBuildDescriptor(System.identityHashCode(gradle), gradle.getGradleVersion(), adapt(gradle.getParent()));
    }
}
