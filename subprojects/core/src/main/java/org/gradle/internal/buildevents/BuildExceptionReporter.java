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
import org.apache.commons.lang.StringUtils;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.ProblemLookup;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.exceptions.CompilationFailedIndicator;
import org.gradle.internal.exceptions.ContextAwareException;
import org.gradle.internal.exceptions.ExceptionContextVisitor;
import org.gradle.internal.exceptions.FailureResolutionAware;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.exceptions.NonGradleCause;
import org.gradle.internal.exceptions.NonGradleCauseExceptionsHolder;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.BufferingStyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.problems.internal.rendering.ProblemRenderer;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nonnull;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.String.join;
import static org.apache.commons.lang.StringUtils.repeat;
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
@NonNullApi
public class BuildExceptionReporter implements Action<Throwable> {
    private static final String NO_ERROR_MESSAGE_INDICATOR = "(no error message)";

    public static final String RESOLUTION_LINE_PREFIX = "> ";
    public static final String LINE_PREFIX_LENGTH_SPACES = repeat(" ", RESOLUTION_LINE_PREFIX.length());

    @NonNullApi
    private enum ExceptionStyle {
        NONE, FULL
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final LoggingConfiguration loggingConfiguration;
    private final BuildClientMetaData clientMetaData;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
        this.textOutputFactory = textOutputFactory;
        this.loggingConfiguration = loggingConfiguration;
        this.clientMetaData = clientMetaData;
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
    }

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData) {
        this(textOutputFactory, loggingConfiguration, clientMetaData, null);
    }

    public void buildFinished(BuildResult result) {
        buildFinished(result, t -> Collections.emptyList());
    }

    public void buildFinished(BuildResult result, ProblemLookup problemLookup) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }
        execute(failure, problemLookup);
    }

    @Override
    public void execute(@Nonnull Throwable failure) {
        execute(failure, t -> Collections.emptyList());
    }

    public void execute(@Nonnull Throwable failure, ProblemLookup problemLookup) {
        if (failure instanceof MultipleBuildFailures) {
            renderMultipleBuildExceptions((MultipleBuildFailures) failure, problemLookup);
        } else {
            renderSingleBuildException(failure, problemLookup);
        }
    }

    private void renderMultipleBuildExceptions(MultipleBuildFailures failure, ProblemLookup problemLookup) {
        String message = failure.getMessage();
        List<? extends Throwable> flattenedFailures = failure.getCauses();
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        output.println();
        output.withStyle(Failure).format("FAILURE: %s", message);
        output.println();

        for (int i = 0; i < flattenedFailures.size(); i++) {
            Throwable cause = flattenedFailures.get(i);
            FailureDetails details = constructFailureDetails("Task", cause, problemLookup);

            output.println();
            output.withStyle(Failure).format("%s: ", i + 1);
            details.summary.writeTo(output.withStyle(Failure));
            output.println();
            output.text("-----------");

            writeFailureDetails(output, details);

            output.println("==============================================================================");
        }
    }

    private void renderSingleBuildException(Throwable failure, ProblemLookup problemLookup) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        FailureDetails details = constructFailureDetails("Build", failure, problemLookup);

        output.println();
        output.withStyle(Failure).text("FAILURE: ");
        details.summary.writeTo(output.withStyle(Failure));
        output.println();

        writeFailureDetails(output, details);
    }

    private static boolean hasCauseAncestry(Throwable failure, Class<?> type) {
        Throwable cause = failure.getCause();
        while (cause != null) {
            if (hasCause(cause, type)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean hasCause(Throwable cause, Class<?> type) {
        if (cause instanceof NonGradleCauseExceptionsHolder) {
            return ((NonGradleCauseExceptionsHolder) cause).hasCause(type);
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

    private FailureDetails constructFailureDetails(String granularity, Throwable failure, ProblemLookup problemLookup) {
        FailureDetails details = new FailureDetails(failure, getShowStackTraceOption());
        details.summary.format("%s failed with an exception.", granularity);

        fillInFailureResolution(details, problemLookup);

        if (failure instanceof ContextAwareException) {
            ((ContextAwareException) failure).accept(new ExceptionFormattingVisitor(details, problemLookup));
        } else {
            details.appendDetails();
        }
        details.renderStackTrace();
        return details;
    }

    private static class ExceptionFormattingVisitor extends ExceptionContextVisitor {
        private final FailureDetails failureDetails;
        private final ProblemLookup problemLookup;

        private final Set<Throwable> printedNodes = new HashSet<>();
        private int depth;
        private int suppressedDuplicateBranchCount;

        private ExceptionFormattingVisitor(FailureDetails failureDetails, ProblemLookup problemLookup) {
            this.failureDetails = failureDetails;
            this.problemLookup = problemLookup;
        }

        @Override
        protected void visitCause(Throwable cause) {
            failureDetails.failure = cause;
            failureDetails.appendDetails();
        }

        @Override
        protected void visitLocation(String location) {
            failureDetails.location.text(location);
        }

        @Override
        public void node(Throwable node) {
            if (shouldBePrinted(node)) {
                printedNodes.add(node);
                if (null == node.getCause() || isUsefulMessage(getMessage(node, problemLookup))) {
                    LinePrefixingStyledTextOutput output = getLinePrefixingStyledTextOutput(failureDetails);
                    renderStyledError(node, output, problemLookup);
                }
            } else {
                // Only increment the suppressed branch count for the ultimate cause of the failure, which has no cause itself
                if (node.getCause() == null) {
                    suppressedDuplicateBranchCount++;
                }
            }
        }

        /**
         * Determines if the given node should be printed.
         *
         * A node should be printed iff it is not in the {@link #printedNodes} set, and it is not a
         * transitive cause of a node that is in the set.  Direct causes will be checked, as well
         * as each branch of {@link ContextAwareException#getReportableCauses()}s for nodes of that type.
         *
         * @param node the node to check
         * @return {@code true} if the node should be printed; {@code false} otherwise
         */
        private boolean shouldBePrinted(Throwable node) {
            if (printedNodes.isEmpty()) {
                return true;
            }

            Queue<Throwable> next = new ArrayDeque<>();
            next.add(node);

            while (!next.isEmpty()) {
                Throwable curr = next.poll();
                if (printedNodes.contains(curr)) {
                    return false;
                } else {
                    if (curr.getCause() != null) {
                        next.add(curr.getCause());
                    }
                    if (curr instanceof ContextAwareException) {
                        next.addAll(((ContextAwareException) curr).getReportableCauses());
                    }
                }
            }

            return true;
        }

        private boolean isUsefulMessage(String message) {
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

    private void fillInFailureResolution(FailureDetails details, ProblemLookup problemLookup) {
        ContextImpl context = new ContextImpl(details.resolution);
        if (details.failure instanceof FailureResolutionAware) {
            ((FailureResolutionAware) details.failure).appendResolutions(context);
        }
        getResolutions(details.failure, problemLookup).stream()
            .distinct()
            .forEach(resolution ->
                context.appendResolution(output ->
                    output.text(join("\n " + LINE_PREFIX_LENGTH_SPACES, resolution.split("\n"))))
            );
        boolean hasNonGradleSpecificCauseInAncestry = hasCauseAncestry(details.failure, NonGradleCause.class);
        if (details.exceptionStyle == ExceptionStyle.NONE && !hasNonGradleSpecificCauseInAncestry) {
            context.appendResolution(output ->
                runWithOption(output, STACKTRACE_LONG_OPTION, " option to get the stack trace.")
            );
        }

        LogLevel logLevel = loggingConfiguration.getLogLevel();
        boolean isLessThanInfo = logLevel.ordinal() > INFO.ordinal();
        if (logLevel != DEBUG && !hasNonGradleSpecificCauseInAncestry) {
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

        if (!hasNonGradleSpecificCauseInAncestry) {
            context.appendResolution(this::writeGeneralTips);
        }
    }

    private static void runWithOption(StyledTextOutput output, String optionName, String text) {
        output.text("Run with ");
        output.withStyle(UserInput).format("--%s", optionName);
        output.text(text);
    }

    private static List<String> getResolutions(Throwable throwable, ProblemLookup problemLookup) {
        ImmutableList.Builder<String> resolutions = ImmutableList.builder();

        if (throwable instanceof ResolutionProvider) {
            resolutions.addAll(((ResolutionProvider) throwable).getResolutions());
        }

        Collection<Problem> all = problemLookup.findAll(throwable);
        for (Problem problem : all) {
            resolutions.addAll(problem.getSolutions());
        }

        for (Throwable cause : getCauses(throwable)) {
            resolutions.addAll(getResolutions(cause, problemLookup));
        }

        return resolutions.build();
    }

    private static List<? extends Throwable> getCauses(Throwable cause) {
        if (cause instanceof MultiCauseException) {
            return ((MultiCauseException) cause).getCauses();
        }
        Throwable nextCause = cause.getCause();
        return nextCause == null ? ImmutableList.of() : ImmutableList.of(nextCause);
    }

    private void addBuildScanMessage(ContextImpl context) {
        context.appendResolution(output -> runWithOption(output, LONG_OPTION, " to get full insights."));
    }

    private boolean isGradleEnterprisePluginApplied() {
        return gradleEnterprisePluginManager != null && gradleEnterprisePluginManager.isPresent();
    }

    private void writeGeneralTips(StyledTextOutput resolution) {
        resolution.text("Get more help at ");
        resolution.withStyle(UserInput).text("https://help.gradle.org");
        resolution.text(".");
    }

    private static String getMessage(Throwable throwable, ProblemLookup problemLookup) {
        try {
            String msg = throwable.getMessage();
            StringBuilder builder = new StringBuilder(msg == null ? "" : msg);
            Collection<Problem> problems = problemLookup.findAll(throwable);
            if (!problems.isEmpty()) {
                builder.append(System.lineSeparator());
                StringWriter problemWriter = new StringWriter();
                new ProblemRenderer(problemWriter).render(new ArrayList<>(problems));
                builder.append(problemWriter);

                // Workaround to keep the original behavior for Java compilation. We should render counters for all problems in the future.
                if (throwable instanceof CompilationFailedIndicator) {
                    String diagnosticCounts = ((CompilationFailedIndicator) throwable).getDiagnosticCounts();
                    if (diagnosticCounts != null) {
                        builder.append(diagnosticCounts);
                    }
                }
            }

            String message = builder.toString();
            if (GUtil.isTrue(message)) {
                return message;
            }
            return String.format("%s %s", throwable.getClass().getName(), NO_ERROR_MESSAGE_INDICATOR);
        } catch (Throwable t) {
            return String.format("Unable to get message for failure of type %s due to %s", throwable.getClass().getSimpleName(), t.getMessage());
        }
    }

    private void writeFailureDetails(StyledTextOutput output, FailureDetails details) {
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

    @NonNullApi
    private static class FailureDetails {
        Throwable failure;
        final BufferingStyledTextOutput summary = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput details = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput location = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput stackTrace = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput resolution = new BufferingStyledTextOutput();
        final ExceptionStyle exceptionStyle;

        public FailureDetails(Throwable failure, ExceptionStyle exceptionStyle) {
            this.failure = failure;
            this.exceptionStyle = exceptionStyle;
        }

        void appendDetails() {
            renderStyledError(failure, details, t -> Collections.emptyList());
        }

        void renderStackTrace() {
            if (exceptionStyle == ExceptionStyle.FULL) {
                try {
                    stackTrace.exception(failure);
                } catch (Throwable t) {
                    // Discard. Should also render this as a separate build failure
                }
            }
        }
    }

    static void renderStyledError(Throwable failure, StyledTextOutput details, ProblemLookup problemLookup) {
        if (failure instanceof StyledException) {
            ((StyledException) failure).render(details);
        } else {
            details.text(getMessage(failure, problemLookup));
        }
    }

    @NonNullApi
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
