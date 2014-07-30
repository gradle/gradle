/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.gradle.BasicGradleTaskSelector;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;

import java.util.List;

/**
 * A client side implementation of {@link org.gradle.tooling.model.gradle.BuildInvocations}
 */
public class ConsumerProvidedBuildInvocations {
    private final List<? extends BasicGradleTaskSelector> selectors;
    private final List<? extends DefaultGradleTask> tasks;

    public ConsumerProvidedBuildInvocations(List<? extends BasicGradleTaskSelector> selectors, List<? extends DefaultGradleTask> tasks) {
        this.selectors = selectors;
        this.tasks = tasks;
    }

    public List<? extends BasicGradleTaskSelector> getTaskSelectors() {
        return selectors;
    }

    public List<? extends DefaultGradleTask> getTasks() {
        return tasks;
    }
}
