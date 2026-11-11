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
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.api.problems.internal.ProblemReportCreator;
import org.gradle.api.problems.internal.ProblemSummarizer;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.api.problems.internal.ProblemsInfrastructure;
import org.gradle.api.problems.internal.DetectionContext;
import org.gradle.api.problems.internal.DetectionContextProvider;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.problems.internal.location.Trace;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
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
    private final DetectionContextProvider detectionContextProvider;
    private final File buildTreeRootDirectory;
    private final ClassFileLookup classFiles = new ClassFileLookup();

    public static final InternalOption<Integer> THRESHOLD_OPTION = InternalOptions.ofInt("org.gradle.internal.problem.summary.threshold", 15);
    public static final int THRESHOLD_DEFAULT_VALUE = THRESHOLD_OPTION.getDefaultValue();

    public DefaultProblemSummarizer(
        BuildOperationProgressEventEmitter eventEmitter,
        CurrentBuildOperationRef currentBuildOperationRef,
        Collection<ProblemEmitter> problemEmitters,
        InternalOptions internalOptions,
        ProblemReportCreator problemReportCreator,
        DetectionContextProvider detectionContextProvider,
        File buildTreeRootDirectory
    ) {
        this.eventEmitter = eventEmitter;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problemEmitters = problemEmitters;
        this.summarizerStrategy = new SummarizerStrategy(internalOptions.getInt(THRESHOLD_OPTION));
        this.problemReportCreator = problemReportCreator;
        this.detectionContextProvider = detectionContextProvider;
        this.buildTreeRootDirectory = buildTreeRootDirectory;
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
    public void emit(ProblemInternal problem, @Nullable OperationIdentifier id) {
        // Detection is computed before summarization throttles occurrences, and attached only to the ones that pass
        DetectionContext detection = detectionContextProvider.detectionContextFor(id);
        TaskIdentity taskIdentity = detection.getTask();
        String taskPath = taskIdentity == null ? null : taskIdentity.getTaskPath();
        // The leaf is part of what tells two problems apart, so it is settled before repeated problems are dropped
        problem = withUserCodeCallers(problem, detection);
        if (summarizerStrategy.shouldEmit(problem, taskPath)) {
            problem = locate(problem, detection);
            problemReportCreator.addProblem(problem);
            for (ProblemEmitter problemEmitter : problemEmitters) {
                problemEmitter.emit(problem, id);
            }
        }
    }

    @Override
    public ProblemInternal locate(ProblemInternal problem, @Nullable OperationIdentifier id) {
        return locate(problem, detectionContextProvider.detectionContextFor(id));
    }

    private ProblemInternal locate(ProblemInternal problem, DetectionContext detection) {
        if (problem.getDetectionTrace() != null) {
            // Already located, by a caller that needed the complete problem before emitting it
            return problem;
        }
        TaskIdentity taskIdentity = detection.getTask();
        problem = withUserCodeCallers(problem, detection);
        return maybeAddTaskLocation(problem, taskIdentity == null ? null : taskIdentity.getTaskPath())
            .withDetectionTrace(DetectionTraces.detectedIn(detection))
            .withOriginTraces(OriginTraces.withLineage(OriginTraces.withFileLocalities(problem.getOriginTraces(), buildTreeRootDirectory), taskIdentity));
    }

    private ProblemInternal withUserCodeCallers(ProblemInternal problem, DetectionContext detection) {
        List<Trace> origins = problem.getOriginTraces();
        List<Trace> withCallers = OriginTraces.withUserCodeCallers(origins, problem.getOriginLocations(), detection, classFiles);
        return withCallers == origins ? problem : problem.withOriginTraces(withCallers);
    }

    private static ProblemInternal maybeAddTaskLocation(ProblemInternal problem, @Nullable String taskPath) {
        if (taskPath != null) {
            problem = problem.toBuilder(new ProblemsInfrastructure(null, null, null, null, null, null, null)).taskLocation(taskPath).build();
        }
        return problem;
    }
}
