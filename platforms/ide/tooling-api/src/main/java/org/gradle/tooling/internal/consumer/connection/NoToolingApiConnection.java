/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.util.List;

import static org.gradle.tooling.model.internal.Exceptions.unsupportedFeature;

/**
 * A {@code ConsumerConnection} implementation for a Gradle version that does not support the tooling API.
 *
 * <p>Used for versions &lt; 1.0-milestone-3.</p>
 */
public class NoToolingApiConnection implements ConsumerConnection {
    private final Distribution distribution;

    public NoToolingApiConnection(Distribution distribution) {
        this.distribution = distribution;
    }

    @Override
    public void stop() {
    }

    @Override
    public String getDisplayName() {
        return distribution.getDisplayName();
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "1.2");
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "1.8");
    }

    @Override
    public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "4.8");
    }

    @Override
    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "2.6");
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, ConsumerOperationParameters operationParameters) {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "6.1");
    }

    @Override
    public void stopWhenIdle(ConsumerOperationParameters operationParameters) {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "6.5");
    }

    @Override
    public void ping(ConsumerOperationParameters operationParameters) {
        throw unsupportedFeature(operationParameters.getEntryPointName(), distribution, "8.12");
    }
}
