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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.CollectionUtils.any;

public class AggregateTestResultsProvider implements TestResultsProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregateTestResultsProvider.class);
    private final Iterable<TestResultsProvider> providers;
    private Map<Long, TestResultsProvider> classOutputProviders;
    private Map<Long, Long> idMappings;

    public AggregateTestResultsProvider(Iterable<TestResultsProvider> providers) {
        this.providers = providers;
    }

    public void visitClasses(final Action<? super TestClassResult> visitor) {
        classOutputProviders = new HashMap<Long, TestResultsProvider>();
        idMappings = new HashMap<Long, Long>();
        final Set<String> seenClasses = new HashSet<String>();
        final long[] newIdCounter = {1};
        for (final TestResultsProvider provider : providers) {
            provider.visitClasses(new Action<TestClassResult>() {
                public void execute(TestClassResult classResult) {
                    if (seenClasses.contains(classResult.getClassName())) {
                        LOGGER.warn("Discarding duplicate results for test class {}.", classResult.getClassName());
                        return;
                    }

                    long newId = newIdCounter[0]++;
                    classOutputProviders.put(newId, provider);
                    idMappings.put(newId, classResult.getId());
                    TestClassResult newIdResult = new OverlayedIdTestClassResult(newId, classResult);
                    visitor.execute(newIdResult);
                }
            });
        }
    }

    private static class OverlayedIdTestClassResult extends TestClassResult {
        public OverlayedIdTestClassResult(long id, TestClassResult delegate) {
            super(id, delegate.getClassName(), delegate.getStartTime());

            for (TestMethodResult result : delegate.getResults()) {
                add(result);
            }
        }
    }

    public boolean hasOutput(long id, TestOutputEvent.Destination destination) {
        return classOutputProviders.get(id).hasOutput(id, destination);
    }

    public void writeAllOutput(long id, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(id).writeAllOutput(id, destination, writer);
    }

    public boolean isHasResults() {
        return any(providers, new Spec<TestResultsProvider>() {
            public boolean isSatisfiedBy(TestResultsProvider element) {
                return element.isHasResults();
            }
        });
    }
    
    public void writeNonTestOutput(long id, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(id).writeNonTestOutput(id, destination, writer);
    }

    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(classId).writeTestOutput(classId, testId, destination, writer);
    }
}
