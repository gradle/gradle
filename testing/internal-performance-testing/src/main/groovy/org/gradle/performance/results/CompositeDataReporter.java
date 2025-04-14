/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList;

import java.io.IOException;

public class CompositeDataReporter<T extends PerformanceTestResult> implements DataReporter<T> {
    private final ImmutableList<DataReporter<? super T>> reporters;

    private CompositeDataReporter(ImmutableList<DataReporter<? super T>> reporters) {
        this.reporters = reporters;
    }

    @SafeVarargs
    public static <S extends PerformanceTestResult> CompositeDataReporter<S> of(DataReporter<? super S>... reporters) {
        return new CompositeDataReporter<S>(ImmutableList.copyOf(reporters));
    }

    @Override
    public void report(T results) {
        for (DataReporter<? super T> reporter : reporters) {
            reporter.report(results);
        }
    }

    @Override
    public void close() throws IOException {
        for (DataReporter<? super T> reporter : reporters) {
            reporter.close();
        }
    }
}
