/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;

public class PatternMatchTestDefinitionProcessor<D extends TestDefinition> implements TestDefinitionProcessor<D> {
    private final TestSelectionMatcher testClassSelectionMatcher;
    private final TestDefinitionProcessor<D> delegate;

    public PatternMatchTestDefinitionProcessor(DefaultTestFilter testFilter, TestDefinitionProcessor<D> delegate) {
        this.testClassSelectionMatcher = new TestSelectionMatcher(testFilter.toSpec());
        this.delegate = delegate;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        delegate.startProcessing(resultProcessor);
    }

    @Override
    public void processTestDefinition(D testDefinition) {
        if (testDefinition.matches(testClassSelectionMatcher)) {
            delegate.processTestDefinition(testDefinition);
        }
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
