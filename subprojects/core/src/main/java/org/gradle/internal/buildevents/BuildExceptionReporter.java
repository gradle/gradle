/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.buildevents;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.exceptions.CompilationFailedIndicator;
import org.gradle.internal.exceptions.ContextAwareException;
import org.gradle.internal.exceptions.FailureResolutionAware;
import org.gradle.internal.exceptions.NonGradleCause;
import org.gradle.internal.exceptions.NonGradleCauseExceptionsHolder;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.BufferingStyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.problems.internal.rendering.ProblemRenderer;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.repeat;
import static org.gradle.api.logging.LogLevel.DEBUG;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption.LONG_OPTION;
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.LogLevelOption.DEBUG_LONG_OPTION;
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.LogLevelOption.INFO_LONG_OPTION;
import static org.gradle.internal.logging.LoggingConfigurationBuildOptions.StacktraceOption.STACKTRACE_LONG_OPTION;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Reports the build exception, if any.
 */
@NullMarked
public class BuildExceptionReporter implements Action<Throwable> {
    private static final String NO_ERROR_MESSAGE_INDICATOR = "(no error message)";

    public static final String RESOLUTION_LINE_PREFIX = "> ";
    public static final String LINE_PREFIX_LENGTH_SPACES = repeat(" ", RESOLUTION_LINE_PREFIX.length());

    @NullMarked
    private enum ExceptionStyle {
        NONE, FULL
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final LoggingConfiguration loggingConfiguration;
    private final BuildClientMetaData clientMetaData;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;
    private final FailureFactory failureFactory;

    public BuildExceptionReporter(
        StyledTextOutputFactory textOutputFactory,
        LoggingConfiguration loggingConfiguration,
        BuildClientMetaData clientMetaData,
        @Nullable GradleEnterprisePluginManager gradleEnterprisePluginManager,
        FailureFactory failureFactory
    ) {
        this.textOutputFactory = textOutputFactory;
        this.loggingConfiguration = loggingConfiguration;
        this.clientMetaData = clientMetaData;
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
        this.failureFactory = failureFactory;
    }

    public BuildExceptionReporter(
        StyledTextOutputFactory textOutputFactory,
        LoggingConfiguration loggingConfiguration,
        BuildClientMetaData clientMetaData,
        FailureFactory failureFactory
    ) {
        this(
            textOutputFactory,
            loggingConfiguration,
            clientMetaData,
            null,
            failureFactory
        );
    }

    public void buildFinished(@Nullable Failure failure) {
        if (failure == null) {
            return;
        }
        renderFailure(failure);
    }

    @Override
    public void execute(@NonNull Throwable throwable) {
        Failure failure = failureFactory.create(throwable);
        renderFailure(failure);
    }

    private void renderFailure(@NonNull Failure failure) {
        List<Failure> causes = failure.getCauses();
        if (causes.size() > 1) {
            renderMultipleBuildExceptions(failure);
        } else {
            renderSingleBuildException(failure);
        }
    }

    private void renderMultipleBuildExceptions(Failure failure) {
        String message = failure.getMessage();
        List<Failure> flattenedFailures = failure.getCauses();
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        output.println();
        output.withStyle(Failure).format("FAILURE: %s", message);
        output.println();

        for (int i = 0; i < flattenedFailures.size(); i++) {
            Failure cause = flattenedFailures.get(i);
            FailureDetails details = constructFailureDetails("Task", cause);

            output.println();
            output.withStyle(Failure).format("%s: ", i + 1);
            details.summary.writeTo(output.withStyle(Failure));
            output.println();
            output.text("-----------");

            writeFailureDetails(output, details);

            output.println("==============================================================================");
        }
    }

    private void renderSingleBuildException(Failure failure) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        FailureDetails details = constructFailureDetails("Build", failure);

        output.println();
        output.withStyle(Failure).text("FAILURE: ");
        details.summary.writeTo(output.withStyle(Failure));
        output.println();

        writeFailureDetails(output, details);
    }

    private static boolean hasCauseAncestry(Failure failure, Class<?> type) {
        Deque<Failure> causes = new ArrayDeque<>(failure.getCauses());
        while (!causes.isEmpty()) {
            Failure cause = causes.pop();
            if (hasCause(cause, type)) {
                return true;
            }
            causes.addAll(cause.getCauses());
        }
        return false;
    }

    private static boolean hasCause(Failure cause, Class<?> type) {
        if (NonGradleCauseExceptionsHolder.class.isAssignableFrom(cause.getExceptionType())) {
            return ((NonGradleCauseExceptionsHolder) cause.getOriginal()).hasCause(type);
        }
        return false;
    }

    private ExceptionStyle getShowStackTraceOption() {
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            return ExceptionStyle.FULL;
        } else {
            return ExceptionStyle.NONE;
        }
    }

    private FailureDetails constructFailureDetails(String granularity, Failure failure) {
        FailureDetails details = new FailureDetails(failure, getShowStackTraceOption());
        details.summary.format("%s failed with an exception.", granularity);

        fillInFailureResolution(details);

        if (failure.getOriginal() instanceof ContextAwareException) {
            ExceptionFormattingVisitor exceptionFormattingVisitor = new ExceptionFormattingVisitor(details);
            ContextAwareExceptionHandler.visit(failure, exceptionFormattingVisitor);
        } else {
            details.appendDetails();
        }
        details.renderStackTrace();
        return details;
    }

    private static class ExceptionFormattingVisitor extends ExceptionContextVisitor {
        private final FailureDetails failureDetails;

        private final Set<Throwable> printedNodes = new HashSet<>();
        private int depth;
        private int suppressedDuplicateBranchCount;

        private ExceptionFormattingVisitor(FailureDetails failureDetails) {
            this.failureDetails = failureDetails;
        }

        @Override
        protected void visitCause(Failure cause) {
            failureDetails.failure = cause;
            failureDetails.appendDetails();
        }

        @Override
        protected void visitLocation(String location) {
            failureDetails.location.text(location);
        }

        @Override
        public void node(Failure node) {
            if (shouldBePrinted(node)) {
                // We use the original here, since identity is not preserved when converting to a Failure.
                // And we still want to report branches even if the most deep exception is the same as the one on another branch.
                // For example, if you run into timeouts when resolving two different dependencies, we still want to report both.
                // And the dependency that is being resolved is only part of the context, not part of the root cause.
                printedNodes.add(node.getOriginal());
                if (node.getCauses().isEmpty() || isUsefulMessage(getMessage(node))) {
                    LinePrefixingStyledTextOutput output = getLinePrefixingStyledTextOutput(failureDetails);
                    renderStyledError(node, output);
                }
            } else {
                // Only increment the suppressed branch count for the ultimate cause of the failure, which has no cause itself
                if (node.getCauses().isEmpty()) {
                    suppressedDuplicateBranchCount++;
                }
            }
        }

        /**
         * Determines if the given node should be printed.
         *
         * A node should be printed iff it is not in the {@link #printedNodes} set, and it is not a
         * transitive cause of a node that is in the set.  Direct causes will be checked, as well
         * as each branch of {@link ContextAwareExceptionHandler#getReportableCauses(Failure)}s for nodes of that type.
         *
         * @param node the node to check
         * @return {@code true} if the node should be printed; {@code false} otherwise
         */
        private boolean shouldBePrinted(Failure node) {
            if (printedNodes.isEmpty()) {
                return true;
            }

            Queue<Failure> next = new ArrayDeque<>();
            next.add(node);

            while (!next.isEmpty()) {
                Failure curr = next.poll();
                if (printedNodes.contains(curr.getOriginal())) {
                    return false;
                } else {
                    if (!curr.getCauses().isEmpty()) {
                        next.add(curr.getCauses().get(0));
                    }
                    if (curr.getOriginal() instanceof ContextAwareException) {
                        next.addAll(ContextAwareExceptionHandler.getReportableCauses(curr));
                    }
                }
            }

            return true;
        }

        private static boolean isUsefulMessage(String message) {
            return StringUtils.isNotBlank(message) && !message.endsWith(NO_ERROR_MESSAGE_INDICATOR);
        }

        @Override
        public void startChildren() {
            depth++;
        }

        @Override
        public void endChildren() {
            depth--;
        }

        private LinePrefixingStyledTextOutput getLinePrefixingStyledTextOutput(FailureDetails details) {
            details.details.format("%n");
            StringBuilder prefix = new StringBuilder(repeat("   ", depth - 1));
            details.details.text(prefix);
            prefix.append("  ");
            details.details.style(Info).text(RESOLUTION_LINE_PREFIX).style(Normal);

            return new LinePrefixingStyledTextOutput(details.details, prefix, false);
        }

        @Override
        protected void endVisiting() {
            if (suppressedDuplicateBranchCount > 0) {
                LinePrefixingStyledTextOutput output = getLinePrefixingStyledTextOutput(failureDetails);
                boolean plural = suppressedDuplicateBranchCount > 1;
                if (plural) {
                    output.append(String.format("There are %d more failures with identical causes.", suppressedDuplicateBranchCount));
                } else {
                    output.append("There is 1 more failure with an identical cause.");
                }
            }
        }
    }

    private void fillInFailureResolution(FailureDetails details) {
        ContextImpl context = new ContextImpl(details.resolution);
        if (details.failure.getOriginal() instanceof FailureResolutionAware) {
            ((FailureResolutionAware) details.failure.getOriginal()).appendResolutions(context);
        }
        getResolutions(details.failure).stream()
            .distinct()
            .forEach(resolution ->
                context.appendResolution(output ->
                    output.text(join("\n " + LINE_PREFIX_LENGTH_SPACES, resolution.split("\n"))))
            );
        boolean shouldDisplayGenericResolutions = !hasCauseAncestry(details.failure, NonGradleCause.class) && !hasProblemReportsWithSolutions(details.failure);
        if (details.exceptionStyle == ExceptionStyle.NONE && shouldDisplayGenericResolutions) {
            context.appendResolution(output ->
                runWithOption(output, STACKTRACE_LONG_OPTION, " option to get the stack trace.")
            );
        }

        LogLevel logLevel = loggingConfiguration.getLogLevel();
        boolean isLessThanInfo = logLevel.ordinal() > INFO.ordinal();
        if (logLevel != DEBUG && shouldDisplayGenericResolutions) {
            context.appendResolution(output -> {
                output.text("Run with ");
                if (isLessThanInfo) {
                    output.withStyle(UserInput).format("--%s", INFO_LONG_OPTION);
                    output.text(" or ");
                }
                output.withStyle(UserInput).format("--%s", DEBUG_LONG_OPTION);
                output.text(" option to get more log output.");
            });
        }

        if (!context.missingBuild && !isGradleEnterprisePluginApplied()) {
            addBuildScanMessage(context);
        }

        if (shouldDisplayGenericResolutions) {
            context.appendResolution(BuildExceptionReporter::writeGeneralTips);
        }
    }

    private static boolean hasProblemReportsWithSolutions(Failure failure) {
        Optional<String> solution = failure.getProblems().stream()
            .flatMap(p -> p.getSolutions().stream())
            .findFirst();
        if (solution.isPresent()) {
            return true;
        } else {
            return hasProblemReportsWithSolutions(failure.getCauses());
        }
    }

    private static boolean hasProblemReportsWithSolutions(List<Failure> failures) {
        return failures.stream().anyMatch(BuildExceptionReporter::hasProblemReportsWithSolutions);
    }

    private static void runWithOption(StyledTextOutput output, String optionName, String text) {
        output.text("Run with ");
        output.withStyle(UserInput).format("--%s", optionName);
        output.text(text);
    }

    private static List<String> getResolutions(Failure failure) {
        ImmutableList.Builder<String> resolutions = ImmutableList.builder();

        if (ResolutionProvider.class.isAssignableFrom(failure.getExceptionType())) {
            resolutions.addAll(((ResolutionProvider) failure.getOriginal()).getResolutions());
        }

        Collection<ProblemInternal> all = failure.getProblems();
        for (ProblemInternal problem : all) {
            resolutions.addAll(problem.getSolutions());
        }

        for (Failure cause : failure.getCauses()) {
            resolutions.addAll(getResolutions(cause));
        }

        return resolutions.build();
    }

    private void addBuildScanMessage(ContextImpl context) {
        context.appendResolution(output -> runWithOption(output, LONG_OPTION, " to generate a Build Scan (Powered by Develocity)."));
    }

    private boolean isGradleEnterprisePluginApplied() {
        return gradleEnterprisePluginManager != null && gradleEnterprisePluginManager.isPresent();
    }

    private static void writeGeneralTips(StyledTextOutput resolution) {
        resolution.text("Get more help at ");
        resolution.withStyle(UserInput).text("https://help.gradle.org");
        resolution.text(".");
    }

    private static String getMessage(Failure failure) {
        try {
            String msg = failure.getMessage();
            StringBuilder builder = new StringBuilder();
            Collection<ProblemInternal> problems = failure.getProblems();
            if (!problems.isEmpty()) {
                // Skip the exception message unless it is a compilation error
                if (failure.getOriginal() instanceof CompilationFailedIndicator) {
                    builder.append(msg == null ? "" : msg);
                    builder.append(System.lineSeparator());
                }
                StringWriter problemWriter = new StringWriter();
                new ProblemRenderer(problemWriter).render(new ArrayList<>(problems));
                builder.append(problemWriter);

                // Workaround to keep the original behavior for Java compilation. We should render counters for all problems in the future.
                if (failure.getOriginal() instanceof CompilationFailedIndicator) {
                    String diagnosticCounts = ((CompilationFailedIndicator) failure.getOriginal()).getDiagnosticCounts();
                    if (diagnosticCounts != null) {
                        builder.append(System.lineSeparator());
                        builder.append(diagnosticCounts);
                    }
                }
            } else {
                builder.append(msg == null ? "" : msg);
            }

            String message = builder.toString();
            if (GUtil.isTrue(message)) {
                return message;
            }
            return String.format("%s %s", failure.getExceptionType().getName(), NO_ERROR_MESSAGE_INDICATOR);
        } catch (Throwable t) {
            return String.format("Unable to get message for failure of type %s due to %s", failure.getExceptionType().getSimpleName(), t.getMessage());
        }
    }

    private static void writeFailureDetails(StyledTextOutput output, FailureDetails details) {
        writeSection(details.location, output, "* Where:");
        writeSection(details.details, output, "* What went wrong:");
        writeSection(details.resolution, output, "* Try:");
        writeSection(details.stackTrace, output, "* Exception is:");
    }

    private static void writeSection(BufferingStyledTextOutput textOutput, StyledTextOutput output, String sectionTitle) {
        if (textOutput.getHasContent()) {
            output.println();
            output.println(sectionTitle);
            textOutput.writeTo(output);
            output.println();
        }
    }

    @NullMarked
    private static class FailureDetails {
        Failure failure;
        final BufferingStyledTextOutput summary = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput details = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput location = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput stackTrace = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput resolution = new BufferingStyledTextOutput();
        final ExceptionStyle exceptionStyle;

        public FailureDetails(Failure failure, ExceptionStyle exceptionStyle) {
            this.failure = failure;
            this.exceptionStyle = exceptionStyle;
        }

        void appendDetails() {
            renderStyledError(failure.withoutProblems(), details);
        }

        void renderStackTrace() {
            if (exceptionStyle == ExceptionStyle.FULL) {
                try {
                    stackTrace.failure(failure);
                } catch (Throwable t) {
                    // Discard. Should also render this as a separate build failure
                }
            }
        }
    }

    static void renderStyledError(Failure failure, StyledTextOutput details) {
        if (failure.getOriginal() instanceof StyledException) {
            ((StyledException) failure.getOriginal()).render(details);
        } else {
            details.text(getMessage(failure));
        }
    }

    @NullMarked
    private class ContextImpl implements FailureResolutionAware.Context {
        private final BufferingStyledTextOutput resolution;

        private final DocumentationRegistry documentationRegistry = new DocumentationRegistry();

        private boolean missingBuild;

        public ContextImpl(BufferingStyledTextOutput resolution) {
            this.resolution = resolution;
        }

        @Override
        public BuildClientMetaData getClientMetaData() {
            return clientMetaData;
        }

        @Override
        public void doNotSuggestResolutionsThatRequireBuildDefinition() {
            missingBuild = true;
        }

        @Override
        public void appendResolution(Consumer<StyledTextOutput> resolutionProducer) {
            if (resolution.getHasContent()) {
                resolution.println();
            }
            resolution.style(Info).text(RESOLUTION_LINE_PREFIX).style(Normal);
            resolutionProducer.accept(resolution);
        }

        @Override
        public void appendDocumentationResolution(String prefix, String userGuideId, String userGuideSection) {
            appendResolution(output -> output.text(documentationRegistry.getDocumentationRecommendationFor(prefix, userGuideId, userGuideSection)));
        }
    }

}
