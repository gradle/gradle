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
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * A {@link TestReportGenerator} that uses multiple report generators to generate reports. A primary generator is used as the user-facing report.
 */
@NullMarked
public class MultiTestReportGenerator implements TestReportGenerator {

    private final TestReportGenerator primary;
    private final List<TestReportGenerator> others;

    public MultiTestReportGenerator(TestReportGenerator primary, List<TestReportGenerator> others) {
        this.primary = primary;
        this.others = others;
    }

    @Nullable
    @Override
    public Path generate(List<Path> resultsDirectories) {
        Path primaryReport = primary.generate(resultsDirectories);
        if (primaryReport == null) {
            return null;
        }
        for (TestReportGenerator other : others) {
            other.generate(resultsDirectories);
        }
        return primaryReport;
    }
}
