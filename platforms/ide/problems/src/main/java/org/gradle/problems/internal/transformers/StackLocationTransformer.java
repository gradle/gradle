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

package org.gradle.problems.internal.transformers;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.locations.FileLocation;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemStream;

public class StackLocationTransformer implements ProblemTransformer {
    private final ProblemStream problemStream;

    public StackLocationTransformer(ProblemStream problemStream) {
        this.problemStream = problemStream;
    }

    @Override
    public Problem transform(Problem problem) {
        if (problem.getException() == null) {
            return problem;
        }

        ProblemDiagnostics problemDiagnostics = problemStream.forCurrentCaller(problem.getException());
        Location loc = problemDiagnostics.getLocation();
        if (loc != null) {
            problem.getLocations().add(new FileLocation(loc.getSourceLongDisplayName().getDisplayName(), loc.getLineNumber(), null, null));
        }
        return problem;
    }
}
