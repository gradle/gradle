/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.util.CollectionUtils.any;

public class AggregateTestResultsProvider implements TestResultsProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregateTestResultsProvider.class);
    private final Iterable<TestResultsProvider> providers;
    private Map<String, TestResultsProvider> classOutputProviders;

    public AggregateTestResultsProvider(Iterable<TestResultsProvider> providers) {
        this.providers = providers;
    }

    public void visitClasses(final Action<? super TestClassResult> visitor) {
        classOutputProviders = new HashMap<String, TestResultsProvider>();
        for (final TestResultsProvider provider : providers) {
            provider.visitClasses(new Action<TestClassResult>() {
                public void execute(TestClassResult classResult) {
                    if (classOutputProviders.containsKey(classResult.getClassName())) {
                        LOGGER.warn("Discarding duplicate results for test class {}.", classResult.getClassName());
                        return;
                    }
                    classOutputProviders.put(classResult.getClassName(), provider);
                    visitor.execute(classResult);
                }
            });
        }
    }

    public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        return classOutputProviders.get(className).hasOutput(className, destination);
    }

    public void writeAllOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(className).writeAllOutput(className, destination, writer);
    }

    public boolean isHasResults() {
        return any(providers, new Spec<TestResultsProvider>() {
            public boolean isSatisfiedBy(TestResultsProvider element) {
                return element.isHasResults();
            }
        });
    }
    
    public void writeNonTestOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(className).writeNonTestOutput(className, destination, writer);
    }

    public void writeTestOutput(String className, Object testId, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(className).writeTestOutput(className, testId, destination, writer);
    }
}
