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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.STACK_TRACE_ELEMENT;

public class ResultAssertion implements Action<ExecutionResult> {
    private int expectedGenericDeprecationWarnings;
    private final List<String> expectedDeprecationWarnings;
    private final boolean expectStackTraces;
    private final boolean checkDeprecations;
    private final boolean checkJdkWarnings;

    public ResultAssertion(
        int expectedGenericDeprecationWarnings, List<String> expectedDeprecationWarnings,
        boolean expectStackTraces, boolean checkDeprecations, boolean checkJdkWarnings
    ) {
        this.expectedGenericDeprecationWarnings = expectedGenericDeprecationWarnings;
        this.expectedDeprecationWarnings = new ArrayList<>(expectedDeprecationWarnings);
        this.expectStackTraces = expectStackTraces;
        this.checkDeprecations = checkDeprecations;
        this.checkJdkWarnings = checkJdkWarnings;
    }

    @Override
    public void execute(ExecutionResult executionResult) {
        String normalizedOutput = executionResult.getNormalizedOutput();
        String error = executionResult.getError();
        boolean executionFailure = executionResult instanceof ExecutionFailure;

        // for tests using rich console standard out and error are combined in output of execution result
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

    public void validate(String output, String displayName) {
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
            } else if (line.matches(".*w: .* is deprecated\\..*")) {
                // A kotlinc warning, ignore
                i++;
            } else if (line.matches("\\[Warn] :.* is deprecated: .*")) {
                // A scalac warning, ignore
                i++;
            } else if (isDeprecationMessageInHelpDescription(line)) {
                i++;
            } else if (removeFirstExpectedDeprecationWarning(line)) {
                // Deprecation warning is expected
                i++;
                i = skipStackTrace(lines, i);
            } else if (line.matches(".*\\s+deprecated.*") && !isConfigurationAllowedUsageChangingInfoLogMessage(line)) {
                if (checkDeprecations && expectedGenericDeprecationWarnings <= 0) {
                    throw new AssertionError(String.format("%s line %d contains a deprecation warning: %s%n=====%n%s%n=====%n", displayName, i + 1, line, output));
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
     * Changes to a configuration's allowed usage contain the string "deprecated" and thus will trigger
     * false positive identification as Deprecation warnings by the logic in {@link #validate(String, String)};
     * this method is used to filter out those false positives.
     * <p>
     * The check for the "this behavior..." string ensures that deprecation warnings in this regard, as opposed
     * to log messages, are not filtered out.
     *
     * @param line the output line to check
     * @return {@code true} if the line is a configuration allowed usage changing info log message; {@code false} otherwise
     */
    private boolean isConfigurationAllowedUsageChangingInfoLogMessage(String line) {
        String msgPrefix = "Allowed usage is changing for configuration";
        return (line.startsWith(msgPrefix) || line.contains("[org.gradle.api.internal.artifacts.configurations.DefaultConfiguration] " + msgPrefix))
            && !line.contains("This behavior has been deprecated.");
    }

    private boolean removeFirstExpectedDeprecationWarning(String line) {
        return expectedDeprecationWarnings.stream().filter(line::contains).findFirst()
            .map(expectedDeprecationWarnings::remove).orElse(false);
    }

    private static int skipStackTrace(List<String> lines, int i) {
        while (i < lines.size() && STACK_TRACE_ELEMENT.matcher(lines.get(i)).matches()) {
            i++;
        }
        return i;
    }

    private boolean isDeprecationMessageInHelpDescription(String s) {
        return s.matches(".*\\[deprecated.*]");
    }
}
