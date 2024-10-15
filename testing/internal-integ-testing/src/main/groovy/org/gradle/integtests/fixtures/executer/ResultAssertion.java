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

package org.gradle.integtests.fixtures.executer;

import com.google.common.io.CharSource;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.STACK_TRACE_ELEMENT;

/**
 * Detects Gradle deprecation warnings in build output.
 *
 * TODO: This class could probably use a better name, maybe something like GradleDeprecationVerifier
 */
public class ResultAssertion implements Action<ExecutionResult> {
    private int expectedGenericDeprecationWarnings;
    private final List<ExpectedDeprecationWarning> expectedDeprecationWarnings;
    private final List<ExpectedDeprecationWarning> maybeExpectedDeprecationWarnings;
    private final boolean expectStackTraces;
    private final boolean checkDeprecations;
    private final boolean checkJdkWarnings;

    /**
     * The last deprecation warning that was matched. This is used to advance lines in the output being scanned
     * by the length of lines in the match.  It will <strong>not</strong> be set back to {@code null} after the
     * first match is found.
     */
    @Nullable
    private ExpectedDeprecationWarning lastMatchedDeprecationWarning = null;

    public ResultAssertion(
        int expectedGenericDeprecationWarnings,
        List<ExpectedDeprecationWarning> expectedDeprecationWarnings,
        List<ExpectedDeprecationWarning> maybeExpectedDeprecationWarnings,
        boolean expectStackTraces,
        boolean checkDeprecations,
        boolean checkJdkWarnings
    ) {
        assert checkDeprecations || expectedDeprecationWarnings.isEmpty() : "Should not expect deprecations when deprecations are not checked";

        this.expectedGenericDeprecationWarnings = expectedGenericDeprecationWarnings;
        this.expectedDeprecationWarnings = new ArrayList<>(expectedDeprecationWarnings);
        this.maybeExpectedDeprecationWarnings = new ArrayList<>(maybeExpectedDeprecationWarnings);
        this.expectStackTraces = expectStackTraces;
        this.checkDeprecations = checkDeprecations;
        this.checkJdkWarnings = checkJdkWarnings;
    }

    @Override
    public void execute(ExecutionResult executionResult) {
        String normalizedOutput = executionResult.getNormalizedOutput();
        String error = executionResult.getError();
        boolean executionFailure = executionResult instanceof ExecutionFailure;

        // For tests using rich console standard out and error are combined in output of execution result
        if (executionFailure) {
            normalizedOutput = removeExceptionStackTraceForFailedExecution(normalizedOutput);
        }

        validate(normalizedOutput, "Standard output");

        if (executionFailure) {
            error = removeExceptionStackTraceForFailedExecution(error);
        }

        validate(error, "Standard error");

        if (!expectedDeprecationWarnings.isEmpty()) {
            throw new AssertionError(String.format("Expected the following deprecation warnings:%n%s",
                expectedDeprecationWarnings.stream()
                    .map(warning -> " - " + warning)
                    .collect(joining("\n"))));
        }
        if (expectedGenericDeprecationWarnings > 0) {
            throw new AssertionError(String.format("Expected %d more deprecation warnings", expectedGenericDeprecationWarnings));
        }
    }

    // Axe everything after the expected exception
    private String removeExceptionStackTraceForFailedExecution(String text) {
        int pos = text.indexOf("* Exception is:");
        if (pos >= 0) {
            text = text.substring(0, pos);
        }
        return text;
    }

    private void validate(String output, String displayName) {
        List<String> lines = getLines(output);
        int i = 0;
        boolean insideVariantDescriptionBlock = false;
        boolean insideKotlinCompilerFlakyStacktrace = false;
        boolean sawVmPluginLoadFailure = false;
        while (i < lines.size()) {
            String line = lines.get(i);
            insideVariantDescriptionBlock = isInsideVariantDescriptionBlock(insideVariantDescriptionBlock, line);

            // https://youtrack.jetbrains.com/issue/KT-29546
            if (line.contains("Compilation with Kotlin compile daemon was not successful")) {
                insideKotlinCompilerFlakyStacktrace = true;
                i++;
            } else if (line.contains("Trying to create VM plugin `org.codehaus.groovy.vmplugin.v9.Java9` by checking `java.lang.Module`")) {
                // a groovy warning when running on Java < 9
                // https://issues.apache.org/jira/browse/GROOVY-9933
                i++; // full stracktrace skipped in next branch
                sawVmPluginLoadFailure = true;
            } else if (line.contains("java.lang.ClassNotFoundException: java.lang.Module") && sawVmPluginLoadFailure) {
                // a groovy warning when running on Java < 9
                // https://issues.apache.org/jira/browse/GROOVY-9933
                i++;
                i = skipStackTrace(lines, i);
            } else if (insideKotlinCompilerFlakyStacktrace &&
                (line.contains("java.rmi.UnmarshalException") ||
                    line.contains("java.io.EOFException")) ||
                // Verbose logging by Jetty when connector is shutdown
                // https://github.com/eclipse/jetty.project/issues/3529
                line.contains("java.nio.channels.CancelledKeyException")) {
                i++;
                i = skipStackTrace(lines, i);
            } else if (line.contains("com.amazonaws.http.IdleConnectionReaper")) {
                /*
                2021-01-05T08:15:51.329+0100 [DEBUG] [com.amazonaws.http.IdleConnectionReaper] Reaper thread:
                java.lang.InterruptedException: sleep interrupted
                    at java.base/java.lang.Thread.sleep(Native Method)
                    at com.amazonaws.http.IdleConnectionReaper.run(IdleConnectionReaper.java:188)
                 */
                i += 2;
                i = skipStackTrace(lines, i);
            } else if (line.matches(".*use(s)? or override(s)? a deprecated API\\.")) {
                // A javac warning, ignore
                i++;
            } else if (line.matches(".*w: .* is deprecated\\..*") || line.matches(".*w: .* This declaration overrides a deprecated member but is not marked as deprecated itself\\..*")) {
                // A kotlinc warning, ignore
                i++;
            } else if (line.matches("\\[Warn] :.* is deprecated: .*")) {
                // A scalac warning, ignore
                i++;
            } else if (line.matches("\\(.*\\) \\[DEP\\d+] DeprecationWarning: .*")) {
                // A node/npm warning, ignore
                i++;
            } else if (isDeprecationMessageInHelpDescription(line)) {
                i++;
            } else if (getNumLinesFromDaemonLogOutput(line) != -1) {
                // Daemon logs may duplicate deprecation warnings
                i += getNumLinesFromDaemonLogOutput(line);
            } else if (removeFirstExpectedDeprecationWarning(lines, i)) {
                i += lastMatchedDeprecationWarning.getNumLines();
                i = skipStackTrace(lines, i);
            } else if (line.matches(".*\\s+deprecated.*")) {
                if (checkDeprecations && expectedGenericDeprecationWarnings <= 0) {
                    StringBuilder message = new StringBuilder(String.format("%s line %d contains an unexpected deprecation warning:%n - %s", displayName, i + 1, line));
                    if (expectedDeprecationWarnings.isEmpty() && maybeExpectedDeprecationWarnings.isEmpty()) {
                        message.append(String.format("%nNo deprecation warnings were expected at this point."));
                    } else {
                        message.append(String.format("%nExpected deprecation warnings:"));
                        expectedDeprecationWarnings.forEach(warning -> message.append(String.format("%n - %s", warning)));
                        maybeExpectedDeprecationWarnings.forEach(warning -> message.append(String.format("%n - (optional) %s", warning)));
                    }
                    message.append(String.format("%n=====%n%s%n=====%n", output));
                    throw new AssertionError(message.toString());
                }
                expectedGenericDeprecationWarnings--;
                // skip over stack trace
                i++;
                i = skipStackTrace(lines, i);
            } else if (!expectStackTraces && !insideVariantDescriptionBlock && STACK_TRACE_ELEMENT.matcher(line).matches() && i < lines.size() - 1 && STACK_TRACE_ELEMENT.matcher(lines.get(i + 1)).matches()) {
                // 2 or more lines that look like stack trace elements
                throw new AssertionError(String.format("%s line %d contains an unexpected stack trace: %s%n=====%n%s%n=====%n", displayName, i + 1, line, output));
            } else if (checkJdkWarnings && line.matches("\\s*WARNING:.*")) {
                throw new AssertionError(String.format("%s line %d contains unexpected JDK warning: %s%n=====%n%s%n=====%n", displayName, i + 1, line, output));
            } else {
                i++;
            }
        }
    }

    private static final Pattern DAEMON_LOG_HEADER_PATTERN = Pattern.compile("----- Last (\\d+) lines from daemon log file - .* -----");
    private static int getNumLinesFromDaemonLogOutput(String line) {
        Matcher m = DAEMON_LOG_HEADER_PATTERN.matcher(line);
        if (m.matches()) {
            return Integer.parseInt(m.group(1)) + 1;
        }
        return -1;
    }

    private static boolean isInsideVariantDescriptionBlock(boolean insideVariantDescriptionBlock, String line) {
        if (insideVariantDescriptionBlock && line.contains("]")) {
            return false;
        }
        if (!insideVariantDescriptionBlock && line.contains("variant \"")) {
            return true;
        }
        return insideVariantDescriptionBlock;
    }

    private static List<String> getLines(String output) {
        try {
            return CharSource.wrap(output).readLines();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Removes the first matching expected deprecation warning from the list of expected deprecations stored
     * in {@link #expectedDeprecationWarnings} as a side-effect; conditional on a matching deprecation warning
     * being already present in that collection.
     * <p>
     * A match means that the first n lines starting at {@code startIdx} match the lines of the
     * expected deprecation warning.  If there are not n lines starting at {@code startIdx} left in
     * the list of lines, then no match is made.
     * <p>
     * As a <em>second</em> side-effect, the last matched deprecation warning is stored in the
     * {@link #lastMatchedDeprecationWarning} field.
     *
     * @param lines the lines of the output
     * @param startIdx the index of the current line of the output to check for a match
     * @return {@code true} if a matching deprecation warning was removed; {@code false} otherwise
     */
    private boolean removeFirstExpectedDeprecationWarning(List<String> lines, int startIdx) {
        Optional<ExpectedDeprecationWarning> matchedWarning =
            Stream.concat(expectedDeprecationWarnings.stream(), maybeExpectedDeprecationWarnings.stream())
                .filter(warning -> warning.matchesNextLines(lines, startIdx))
                .findFirst();
        if (matchedWarning.isPresent()) {
            lastMatchedDeprecationWarning = matchedWarning.get();
            expectedDeprecationWarnings.remove(lastMatchedDeprecationWarning);
        }
        return matchedWarning.isPresent();
    }

    private static int skipStackTrace(List<String> lines, int i) {
        while (i < lines.size() && STACK_TRACE_ELEMENT.matcher(lines.get(i)).matches()) {
            i++;
        }
        return i;
    }

    private static boolean isDeprecationMessageInHelpDescription(String s) {
        return s.matches(".*\\[deprecated.*]");
    }
}
