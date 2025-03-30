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

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared interface between legacy/JVM and generic test report generators.
 */
public interface TestReportGenerator {
    /**
     * A test report generator that does nothing.
     */
    TestReportGenerator NO_OP = new TestReportGenerator() {
        @Nullable
        @Override
        public Path generate(List<Path> resultsDirectories) {
            return null;
        }
    };

    /**
     * Generate a report from the test results in the given results directories, if there are any.
     *
     * <p>
     * Results are placed in the given report directory.
     * </p>
     *
     * @param resultsDirectories the directories containing the test results
     * @return the path to the main file/directory of the generated report, or {@code null} if no report was generated
     */
    @Nullable
    Path generate(List<Path> resultsDirectories);
}
