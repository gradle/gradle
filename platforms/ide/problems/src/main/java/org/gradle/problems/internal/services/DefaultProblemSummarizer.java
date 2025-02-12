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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.api.problems.internal.ProblemReportCreator;
import org.gradle.api.problems.internal.ProblemSummarizer;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.api.problems.internal.ProblemsInfrastructure;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.problems.internal.TaskIdentityProvider;
import org.gradle.internal.buildoption.IntegerInternalOption;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class DefaultProblemSummarizer implements ProblemSummarizer {

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Collection<ProblemEmitter> problemEmitters;
    private final ProblemReportCreator problemReportCreator;
    private final SummarizerStrategy summarizerStrategy;
    private final TaskIdentityProvider taskProvider;

    public static final InternalOption<Integer> THRESHOLD_OPTION = new IntegerInternalOption("org.gradle.internal.problem.summary.threshold", 15);
    public static final int THRESHOLD_DEFAULT_VALUE = THRESHOLD_OPTION.getDefaultValue();

    public DefaultProblemSummarizer(
        BuildOperationProgressEventEmitter eventEmitter,
        CurrentBuildOperationRef currentBuildOperationRef,
        Collection<ProblemEmitter> problemEmitters,
        InternalOptions internalOptions,
        ProblemReportCreator problemReportCreator,
        TaskIdentityProvider taskProvider
    ) {
        this.eventEmitter = eventEmitter;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problemEmitters = problemEmitters;
        this.summarizerStrategy = new SummarizerStrategy(internalOptions.getOption(THRESHOLD_OPTION).get());
        this.problemReportCreator = problemReportCreator;
        this.taskProvider = taskProvider;
    }

    @Override
    public String getId() {
        return "problem summarizer";
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        List<ProblemSummaryData> cutOffProblems = summarizerStrategy.getCutOffProblems();
        problemReportCreator.createReportFile(reportDir, cutOffProblems);
        eventEmitter.emitNow(currentBuildOperationRef.getId(), new DefaultProblemsSummaryProgressDetails(cutOffProblems));
    }

    @Override
    public void emit(InternalProblem problem, @Nullable OperationIdentifier id) {
        if (summarizerStrategy.shouldEmit(problem)) {
            problem = maybeAddTaskLocation(problem, id);
            problemReportCreator.addProblem(problem);
            for (ProblemEmitter problemEmitter : problemEmitters) {
                problemEmitter.emit(problem, id);
            }
        }
    }

    @NonNull
    private InternalProblem maybeAddTaskLocation(InternalProblem problem, @Nullable OperationIdentifier id) {
        TaskIdentity taskIdentity = taskProvider.taskIdentityFor(id);
        if (taskIdentity != null) {
            problem = problem.toBuilder(new ProblemsInfrastructure(null, null, null, null, null, null)).taskLocation(taskIdentity.getTaskPath()).build();
        }
        return problem;
    }
}
