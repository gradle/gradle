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

package org.gradle.performance.results;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class FilteredResultsStore implements ResultsStore, Closeable {
    private final AllResultsStore delegate;
    private final Pattern pattern;

    public FilteredResultsStore(AllResultsStore delegate, Pattern pattern) {
        this.delegate = delegate;
        this.pattern = pattern;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public List<String> getTestNames() {
        Iterable<String> filtered = Iterables.filter(delegate.getTestNames(), new Predicate<String>() {
            @Override
            public boolean apply(@Nullable String input) {
                return pattern.matcher(input).matches();
            }
        });
        return Lists.newArrayList(filtered);
    }

    @Override
    public TestExecutionHistory getTestResults(String testName) {
        return delegate.getTestResults(testName);
    }

    @Override
    public TestExecutionHistory getTestResults(String testName, int mostRecentN) {
        return delegate.getTestResults(testName, mostRecentN);
    }
}
