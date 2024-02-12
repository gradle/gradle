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

import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.api.problems.internal.ProblemReport;
import org.gradle.api.problems.internal.ProblemTransformer;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemStream;

public class ProblemStreamLocationTransformer implements ProblemTransformer {

    private final ProblemStream problemStream;

    public ProblemStreamLocationTransformer(ProblemStream problemStream) {
        this.problemStream = problemStream;
    }

    @Override
    public ProblemReport transform(ProblemReport problem, OperationIdentifier id) {
        ProblemDiagnostics problemDiagnostics = problemStream.forCurrentCaller(problem.getContext().getException());
        Location loc = problemDiagnostics.getLocation();
        InternalProblemBuilder builder = problem.toBuilder();
        if (loc != null) {
            builder.lineInFileLocation(loc.getSourceLongDisplayName().getDisplayName(), loc.getLineNumber());
        }
        if (problemDiagnostics.getSource() != null && problemDiagnostics.getSource().getPluginId() != null) {
            builder.pluginLocation(problemDiagnostics.getSource().getPluginId()).build();
        }
        return builder.build();
    }
}
