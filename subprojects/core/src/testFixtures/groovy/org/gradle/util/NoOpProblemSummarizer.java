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

package org.gradle.util;

import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemSummarizer;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;
import java.io.File;

public class NoOpProblemSummarizer implements ProblemSummarizer {

    @Override
    public void emit(Problem problem, @Nullable OperationIdentifier id) {
        // no op
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        //no op
    }
}
