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

package org.gradle.api.problems.internal;

import org.gradle.api.Action;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroups;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.operations.BuildOperationIdRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.internal.rendering.ProblemWriter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.StringWriter;
import java.util.Collection;

public class DefaultProblemReporter implements ProblemReporterInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProblemReporter.class);
    private static final String RELATIVE_PATH = "Reported problem has a relative path as its location";

    private final ProblemSummarizer problemSummarizer;
    private final ProblemsInfrastructure infrastructure;
    private final BuildOperationIdRef operationIdRef;
    private final ExceptionProblemRegistry exceptionProblemRegistry;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultProblemReporter(
        ProblemSummarizer problemSummarizer,
        BuildOperationIdRef operationIdRef,
        ExceptionProblemRegistry exceptionProblemRegistry,
        ExceptionAnalyser exceptionAnalyser,
        ProblemsInfrastructure infrastructure
    ) {
        this.problemSummarizer = problemSummarizer;
        this.infrastructure = infrastructure;
        this.operationIdRef = operationIdRef;
        this.exceptionProblemRegistry = exceptionProblemRegistry;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    @Override
    public void report(ProblemId problemId, Action<? super ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        problemBuilder.id(problemId);
        spec.execute(problemBuilder);
        ProblemInternal problem = problemBuilder.build();
        warnAboutRelativePaths(problem);
        report(problem);
    }

    /**
     * The file location methods of the public write API take a path as a plain string, and it must be absolute:
     * nothing says what a relative path is relative to, so Gradle can't tell whether the file belongs to the build.
     * The problem is still reported as given; this adds a problem about the path, for the author of the reporting code.
     */
    private void warnAboutRelativePaths(ProblemInternal problem) {
        ProblemGroups groups = infrastructure.getGroups();
        if (groups == null) {
            return;
        }
        for (ProblemLocation location : problem.getOriginLocations()) {
            if (location instanceof FileLocation && !new File(((FileLocation) location).getPath()).isAbsolute()) {
                String path = ((FileLocation) location).getPath();
                DefaultProblemBuilder misuse = createProblemBuilder();
                misuse.id(groups.getGradle().getBuildLogic().problemId(RELATIVE_PATH))
                    .contextualLabel("Problem '" + DefaultProblemId.displayNameOf(problem.getDefinition().getId()) + "' gives the relative path '" + path + "' as its location")
                    .details("The paths given to the file location methods of ProblemSpec must be absolute. Gradle can't tell where a relative path points, so it can't say whether the file belongs to the build.")
                    .solution("Pass an absolute path to fileLocation, lineInFileLocation or offsetInFileLocation")
                    .internalSeverity(Severity.WARNING);
                report(misuse.build());
                // One is enough to make the point
                return;
            }
        }
    }

    @NonNull
    private DefaultProblemBuilder createProblemBuilder() {
        return new DefaultProblemBuilder(infrastructure);
    }

    @Override
    public void reportError(Problem problem) {
        problem = getBuilder(problem).internalSeverity(Severity.ERROR).build();
        report(problem);
    }

    @Override
    public void reportError(Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            reportError(problem);
        }
    }

    @Override
    public RuntimeException throwing(Throwable exception, ProblemId problemId, Action<? super ProblemSpec> spec) {
        DefaultProblemBuilder problemBuilder = createProblemBuilder();
        problemBuilder.id(problemId);
        spec.execute(problemBuilder);
        problemBuilder.withException(exception);
        ProblemInternal problem = problemBuilder.build();
        warnAboutRelativePaths(problem);
        report(addExceptionToProblem(exception, problem));
        throw runtimeException(exception);
    }

    @Override
    public RuntimeException throwing(Throwable exception, Problem problem) {
        problem = addExceptionToProblem(exception, problem);
        report(problem);
        throw runtimeException(exception);
    }

    @Override
    public RuntimeException throwing(Throwable exception, Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            report(addExceptionToProblem(exception, problem));
        }
        throw runtimeException(exception);
    }

    @NonNull
    private ProblemInternal addExceptionToProblem(Throwable exception, Problem problem) {
        return getBuilder(problem).internalSeverity(Severity.ERROR).withException(transform(exception)).build();
    }

    private static RuntimeException runtimeException(Throwable exception) {
        if (exception instanceof RuntimeException) {
            return (RuntimeException) exception;
        } else {
            return new RuntimeException(exception);
        }
    }

    @Override
    public Problem create(ProblemId problemId, Action<? super ProblemSpec> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        defaultProblemBuilder.id(problemId);
        action.execute(defaultProblemBuilder);
        ProblemInternal problem = defaultProblemBuilder.build();
        warnAboutRelativePaths(problem);
        return problem;
    }

    @Override
    public ProblemInternal internalCreate(Action<? super ProblemSpecInternal> action) {
        DefaultProblemBuilder defaultProblemBuilder = createProblemBuilder();
        action.execute(defaultProblemBuilder);
        return defaultProblemBuilder.build();
    }

    /**
     * Reports a problem.
     * <p>
     * The problem is attributed to the build operation provided by this reporter's
     * {@link BuildOperationIdRef} — typically the current build operation of the reporting
     * thread, falling back to the root build operation for threads that have no current
     * operation (for example, a thread dispatching build events to user-provided listeners).
     * Only when no operation is available at all is the problem discarded.
     *
     * @param problem The problem to report.
     */
    @Override
    public void report(Problem problem) {
        OperationIdentifier id = operationIdRef.getId();
        if (id != null) {
            report(problem, id);
        } else {
            LOGGER.warn(discardedProblemMessage((ProblemInternal) problem));
        }
    }

    static String discardedProblemMessage(ProblemInternal problem) {
        StringWriter message = new StringWriter();
        message.append("Discarding problem, no build operation is available to attribute it to on this thread:").append(System.lineSeparator());
        ProblemWriter.simple().write(problem, message);
        return message.toString();
    }

    @Override
    public void report(Collection<? extends Problem> problems) {
        for (Problem problem : problems) {
            report(problem);
        }
    }

    /**
     * Reports a problem with an explicit operation identifier.
     * <p>
     * The operation identifier should not be null,
     * otherwise the behavior will be defined by the used {@link ProblemEmitter}.
     *
     * @param problem The problem to report.
     * @param id The operation identifier to associate with the problem.
     */
    @Override
    public void report(Problem problem, OperationIdentifier id) {
        ProblemInternal problemInternal = withCanonicalId((ProblemInternal) problem);
        Throwable exception = problemInternal.getException();
        if (exception != null) {
            // The copy kept against the exception is what a build failure carries, so it gets its locations first
            problemInternal = problemSummarizer.locate(problemInternal, id);
            exceptionProblemRegistry.onProblem(transform(exception), problemInternal);
        }
        problemSummarizer.emit(problemInternal, id);
    }

    /**
     * Reports problems whose groups were created through the legacy factories with the matching predefined groups, so
     * that every consumer, including the failure's problems, sees the same group whichever API the producer used.
     */
    private static ProblemInternal withCanonicalId(ProblemInternal problem) {
        ProblemId id = problem.getDefinition().getId();
        ProblemId canonical = DefaultProblemGroups.INSTANCE.canonical(id);
        return canonical == id ? problem : problem.withId(canonical);
    }

    @NonNull
    private ProblemBuilderInternal getBuilder(Problem problem) {
        return ((ProblemInternal) problem).toBuilder(infrastructure);
    }

    private Throwable transform(Throwable failure) {
        if (exceptionAnalyser == null) {
            return failure;
        }
        try {
            return exceptionAnalyser.transform(failure).getCause();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
