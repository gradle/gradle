/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * A {@link TestReportGenerator} that uses multiple report generators to generate reports. A primary generator is used as the user-facing report.
 */
@NullMarked
public final class MultiTestReportGenerator implements TestReportGenerator {

    private final TestReportGenerator primary;
    private final Set<TestReportGenerator> others;

    public MultiTestReportGenerator(TestReportGenerator primary, Set<TestReportGenerator> others) {
        if (others.contains(primary)) {
            throw new IllegalArgumentException(
                "The primary report generator must not be in the set of other report generators."
            );
        }
        this.primary = primary;
        this.others = others;
    }

    @Override
    public Path generate(List<Path> resultsDirectories) {
        Path primaryReport = primary.generate(resultsDirectories);
        for (TestReportGenerator other : others) {
            other.generate(resultsDirectories);
        }
        return primaryReport;
    }
}
