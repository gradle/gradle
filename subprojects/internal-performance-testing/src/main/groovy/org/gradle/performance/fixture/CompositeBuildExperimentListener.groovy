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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
class CompositeBuildExperimentListener implements BuildExperimentListener {
    private List<BuildExperimentListener> listeners = []

    @Override
    void beforeExperiment(BuildExperimentSpec experimentSpec, File projectDir) {
        listeners.each { it.beforeExperiment(experimentSpec, projectDir) }
    }

    @Override
    void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
        listeners.each { it.beforeInvocation(invocationInfo) }
    }

    @Override
    void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
        listeners.each { it.afterInvocation(invocationInfo, operation, measurementCallback) }
    }

    void addListener(BuildExperimentListener listener) {
        listeners.add(listener)
    }
}
