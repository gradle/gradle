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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.tooling.events.ProgressListener;

import java.util.List;

public class BuildProgressListenerConfiguration {

    private final List<ProgressListener> testProgressListeners;
    private final List<ProgressListener> taskProgressListeners;
    private final List<ProgressListener> buildOperationProgressListeners;

    public BuildProgressListenerConfiguration(
        List<ProgressListener> testProgressListeners,
        List<ProgressListener> taskProgressListeners,
        List<ProgressListener> buildOperationProgressListeners) {
        this.testProgressListeners = testProgressListeners;
        this.taskProgressListeners = taskProgressListeners;
        this.buildOperationProgressListeners = buildOperationProgressListeners;
    }

    public List<ProgressListener> getTestProgressListeners() {
        return testProgressListeners;
    }

    public List<ProgressListener> getTaskProgressListeners() {
        return taskProgressListeners;
    }

    public List<ProgressListener> getBuildOperationProgressListeners() {
        return buildOperationProgressListeners;
    }

}
