/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.problems.transformers;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.internal.problems.ProblemLocationAnalyzer;
import org.gradle.problems.Location;

import java.util.Arrays;
import java.util.List;

public class StackLocationTransformer implements ProblemTransformer {

    private final ProblemLocationAnalyzer problemLocationAnalyzer;

    public StackLocationTransformer(ProblemLocationAnalyzer problemLocationAnalyzer) {
        this.problemLocationAnalyzer = problemLocationAnalyzer;
    }

    @Override
    public Problem transform(Problem problem) {
        if (problem.getException() != null) {
            Throwable throwable = problem.getException();

            // Converts the array of stack trace elements to an array list
            List<StackTraceElement> stackTraceElements = Arrays.asList(throwable.getStackTrace());
            Location location = problemLocationAnalyzer.locationForUsage(stackTraceElements, true);

            if (location != null) {
                System.out.println(location);
                //problem.getWhere().add(location);
            }
        }

        return problem;
    }
}
