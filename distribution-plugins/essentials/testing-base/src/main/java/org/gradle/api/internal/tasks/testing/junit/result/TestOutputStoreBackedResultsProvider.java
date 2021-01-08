/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

public abstract class TestOutputStoreBackedResultsProvider implements TestResultsProvider {
    private final TestOutputStore outputStore;
    private final ConcurrentMap<Thread, TestOutputStore.Reader> readers;

    public TestOutputStoreBackedResultsProvider(TestOutputStore outputStore) {
        this.outputStore = outputStore;
        this.readers = Maps.newConcurrentMap();
    }

    protected void withReader(Action<TestOutputStore.Reader> action) {
        action.execute(getReader());
    }

    private TestOutputStore.Reader getReader() {
        Thread thread = Thread.currentThread();
        TestOutputStore.Reader reader = readers.get(thread);
        if (reader == null) {
            reader = outputStore.reader();
            readers.put(thread, reader);
        }
        return reader;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(readers.values()).stop();
    }

}
