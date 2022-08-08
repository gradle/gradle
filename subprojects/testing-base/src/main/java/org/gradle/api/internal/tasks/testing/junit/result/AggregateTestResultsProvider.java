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

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.util.internal.CollectionUtils.any;

public class AggregateTestResultsProvider implements TestResultsProvider {
    private final Iterable<TestResultsProvider> providers;
    private Multimap<Long, DelegateProvider> classOutputProviders;

    public AggregateTestResultsProvider(Iterable<TestResultsProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void visitClasses(final Action<? super TestClassResult> visitor) {
        final Map<String, OverlaidIdProxyingTestClassResult> aggregatedTestResults = new LinkedHashMap<String, OverlaidIdProxyingTestClassResult>();
        classOutputProviders = ArrayListMultimap.create();
        final AtomicLong newIdCounter = new AtomicLong(0L);
        for (final TestResultsProvider provider : providers) {
            provider.visitClasses(new Action<TestClassResult>() {
                @Override
                public void execute(final TestClassResult classResult) {
                    OverlaidIdProxyingTestClassResult newTestResult = aggregatedTestResults.get(classResult.getClassName());
                    if (newTestResult != null) {
                        newTestResult.addTestClassResult(classResult);
                    } else {
                        long newId = newIdCounter.incrementAndGet();
                        newTestResult = new OverlaidIdProxyingTestClassResult(newId, classResult);
                        aggregatedTestResults.put(classResult.getClassName(), newTestResult);
                    }
                    classOutputProviders.put(newTestResult.getId(), new DelegateProvider(classResult.getId(), provider));
                }
            });
        }
        for (OverlaidIdProxyingTestClassResult classResult : aggregatedTestResults.values()) {
            visitor.execute(classResult);
        }
    }

    private static class DelegateProvider {
        private final long id;
        private final TestResultsProvider provider;

        private DelegateProvider(long id, TestResultsProvider provider) {
            this.id = id;
            this.provider = provider;
        }
    }

    private static class OverlaidIdProxyingTestClassResult extends TestClassResult {
        private final Map<Long, TestClassResult> delegates = new LinkedHashMap<Long, TestClassResult>();

        public OverlaidIdProxyingTestClassResult(long id, TestClassResult delegate) {
            super(id, delegate.getClassName(), delegate.getStartTime());
            addTestClassResult(delegate);
        }

        void addTestClassResult(TestClassResult delegate) {
            Preconditions.checkArgument(delegates.isEmpty() || delegates.values().iterator().next().getClassName().equals(delegate.getClassName()));
            delegates.put(delegate.getId(), delegate);
            for (TestMethodResult result : delegate.getResults()) {
                add(result);
            }
            if (delegate.getStartTime() < getStartTime()) {
                setStartTime(delegate.getStartTime());
            }
        }
    }

    @Override
    public boolean hasOutput(long classId, final TestOutputEvent.Destination destination) {
        for (DelegateProvider delegateProvider : classOutputProviders.get(classId)) {
            if (delegateProvider.provider.hasOutput(delegateProvider.id, destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasOutput(long classId, final long testId, final TestOutputEvent.Destination destination) {
        for (DelegateProvider delegateProvider : classOutputProviders.get(classId)) {
            if (delegateProvider.provider.hasOutput(delegateProvider.id, testId, destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeAllOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        for (DelegateProvider delegateProvider : classOutputProviders.get(classId)) {
            delegateProvider.provider.writeAllOutput(delegateProvider.id, destination, writer);
        }
    }

    @Override
    public boolean isHasResults() {
        return any(providers, new Spec<TestResultsProvider>() {
            @Override
            public boolean isSatisfiedBy(TestResultsProvider element) {
                return element.isHasResults();
            }
        });
    }

    @Override
    public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        for (DelegateProvider delegateProvider : classOutputProviders.get(classId)) {
            delegateProvider.provider.writeNonTestOutput(delegateProvider.id, destination, writer);
        }
    }

    @Override
    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        for (DelegateProvider delegateProvider : classOutputProviders.get(classId)) {
            delegateProvider.provider.writeTestOutput(delegateProvider.id, testId, destination, writer);
        }
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(providers).stop();
    }
}
