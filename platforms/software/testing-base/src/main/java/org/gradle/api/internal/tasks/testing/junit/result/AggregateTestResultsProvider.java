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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class AggregateTestResultsProvider implements TestResultsProvider {
    private final Iterable<TestResultsProvider> delegates;
    private final PersistentTestResult result;

    public AggregateTestResultsProvider(Iterable<TestResultsProvider> delegates) {
        Iterator<TestResultsProvider> iterator = delegates.iterator();
        Preconditions.checkArgument(iterator.hasNext(), "At least one delegate is required");
        this.delegates = delegates;
        PersistentTestResult result = iterator.next().getResult();
        while (iterator.hasNext()) {
            result = result.merge(iterator.next().getResult());
        }
        this.result = result;
    }

    @Override
    public void visitChildren(Action<? super TestResultsProvider> visitor) {
        ListMultimap<String, TestResultsProvider> childProvidersByName = Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);
        for (final TestResultsProvider provider : delegates) {
            provider.visitChildren(childProvider -> {
                String name = childProvider.getResult().getName();
                childProvidersByName.put(name, childProvider);
            });
        }
        for (List<TestResultsProvider> providers : Multimaps.asMap(childProvidersByName).values()) {
            // Optimize by dropping the wrapper if there is only one provider
            if (providers.size() == 1) {
                visitor.execute(providers.get(0));
            } else {
                visitor.execute(new AggregateTestResultsProvider(providers));
            }
        }
    }

    @Override
    public PersistentTestResult getResult() {
        return result;
    }

    @Override
    public void copyOutput(TestOutputEvent.Destination destination, Writer writer) {
        for (TestResultsProvider delegate : delegates) {
            delegate.copyOutput(destination, writer);
        }
    }

    @Override
    public boolean hasOutput(TestOutputEvent.Destination destination) {
        for (TestResultsProvider delegate : delegates) {
            if (delegate.hasOutput(destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasChildren() {
        for (TestResultsProvider delegate : delegates) {
            if (delegate.hasChildren()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(delegates).stop();
    }
}
