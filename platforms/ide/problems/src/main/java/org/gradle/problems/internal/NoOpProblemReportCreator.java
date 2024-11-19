/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.problems.internal;


import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.ProblemReportCreator;
import org.gradle.api.problems.internal.ProblemSummaryData;

import java.io.File;
import java.util.List;

public class NoOpProblemReportCreator implements ProblemReportCreator {
    @Override
    public void createReportFile(File reportDir, List<ProblemSummaryData> cutOffProblems) {
        // no op
    }

    @Override
    public void addProblem(Problem problem) {
        // no op
    }
}
