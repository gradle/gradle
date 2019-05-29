/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.internal.Pair;
import org.gradle.util.TextUtil;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.gradle.util.Matchers.isEmpty;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

public class OutputScrapingExecutionFailure extends OutputScrapingExecutionResult implements ExecutionFailure {
    private static final Pattern FAILURE_PATTERN = Pattern.compile("FAILURE: (.+)");
    private static final Pattern CAUSE_PATTERN = Pattern.compile("(?m)(^\\s*> )");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?ms)^\\* What went wrong:$(.+?)^\\* Try:$");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?ms)^\\* Where:((.+?)'.+?') line: (\\d+)$");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("(?ms)^\\* Try:$(.+?)^\\* Exception is:$");
    private final String summary;
    private final List<String> descriptions = new ArrayList<String>();
    private final List<String> lineNumbers = new ArrayList<String>();
    private final List<String> fileNames = new ArrayList<String>();
    private final String resolution;
    // with normalized line endings
    private final List<String> causes = new ArrayList<String>();
    private final LogContent mainContent;

    static boolean hasFailure(String error) {
        return FAILURE_PATTERN.matcher(error).find();
    }

    /**
     * Creates a result from the output of a <em>single</em> Gradle invocation.
     *
     * @param output The raw build stdout chars.
     * @param error The raw build stderr chars.
     * @return A {@link OutputScrapingExecutionResult} for a successful build, or a {@link OutputScrapingExecutionFailure} for a failed build.
     */
    public static OutputScrapingExecutionFailure from(String output, String error) {
        return new OutputScrapingExecutionFailure(output, error);
    }

    protected OutputScrapingExecutionFailure(String output, String error) {
        super(LogContent.of(output), LogContent.of(error));

        LogContent withoutDebug = LogContent.of(output).ansiCharsToPlainText().removeDebugPrefix();

        // Find failure section
        Pair<LogContent, LogContent> match = withoutDebug.splitOnFirstMatchingLine(FAILURE_PATTERN);
        if (match == null) {
            // Not present in output, check error output.
            match = LogContent.of(error).ansiCharsToPlainText().removeDebugPrefix().splitOnFirstMatchingLine(FAILURE_PATTERN);
            if (match != null) {
                match = Pair.of(withoutDebug, match.getRight());
            } else {
                // Not present, assume no failure details
                match = Pair.of(withoutDebug, LogContent.empty());
            }
        } else {
            if (match.getRight().countMatches(FAILURE_PATTERN) != 1) {
                throw new IllegalArgumentException("Found multiple failure sections in log output: " + output);
            }
        }

        LogContent failureContent = match.getRight();
        this.mainContent = match.getLeft();

        String failureText = failureContent.withNormalizedEol();

        java.util.regex.Matcher matcher = FAILURE_PATTERN.matcher(failureText);
        if (matcher.lookingAt()) {
            summary = matcher.group(1);
        } else {
            summary = "";
        }

        matcher = LOCATION_PATTERN.matcher(failureText);
        while (matcher.find()) {
            fileNames.add(matcher.group(1).trim());
            lineNumbers.add(matcher.group(3));
        }

        matcher = DESCRIPTION_PATTERN.matcher(failureText);
        while (matcher.find()) {
            String problemStr = matcher.group(1);
            Problem problem = extract(problemStr);
            descriptions.add(problem.description);
            causes.addAll(problem.causes);
        }

        matcher = RESOLUTION_PATTERN.matcher(failureText);
        if (!matcher.find()) {
            resolution = "";
        } else {
            resolution = matcher.group(1).trim();
        }
    }

    @Override
    public LogContent getMainContent() {
        return mainContent;
    }

    private Problem extract(String problem) {
        java.util.regex.Matcher matcher = CAUSE_PATTERN.matcher(problem);
        String description;
        List<String> causes = new ArrayList<String>();
        if (!matcher.find()) {
            description = TextUtil.normaliseLineSeparators(problem.trim());
        } else {
            description = TextUtil.normaliseLineSeparators(problem.substring(0, matcher.start()).trim());
            while (true) {
                int pos = matcher.end();
                int prefix = matcher.group(1).length();
                String prefixPattern = toPrefixPattern(prefix);
                if (matcher.find(pos)) {
                    String cause = TextUtil.normaliseLineSeparators(problem.substring(pos, matcher.start()).trim().replaceAll(prefixPattern, ""));
                    causes.add(cause);
                } else {
                    String cause = TextUtil.normaliseLineSeparators(problem.substring(pos).trim().replaceAll(prefixPattern, ""));
                    causes.add(cause);
                    break;
                }
            }
        }
        return new Problem(description, causes);
    }

    private String toPrefixPattern(int prefix) {
        StringBuilder builder = new StringBuilder("(?m)^");
        for (int i = 0; i < prefix; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    @Override
    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        assertThat(this.lineNumbers, hasItem(equalTo(String.valueOf(lineNumber))));
        return this;
    }

    @Override
    public ExecutionFailure assertHasFileName(String filename) {
        assertThat(this.fileNames, hasItem(equalTo(filename)));
        return this;
    }

    @Override
    public ExecutionFailure assertHasFailures(int count) {
        assertThat(this.descriptions.size(), equalTo(count));
        if (count == 1) {
            assertThat(summary, equalTo("Build failed with an exception."));
        } else {
            assertThat(summary, equalTo(String.format("Build completed with %s failures.", count)));
        }
        return this;
    }

    @Override
    public ExecutionFailure assertHasCause(String description) {
        assertThatCause(startsWith(description));
        return this;
    }

    @Override
    public ExecutionFailure assertThatCause(Matcher<String> matcher) {
        for (String cause : causes) {
            if (matcher.matches(cause)) {
                return this;
            }
        }
        failureOnUnexpectedOutput(String.format("No matching cause found in %s", causes));
        return this;
    }

    @Override
    public ExecutionFailure assertHasResolution(String resolution) {
        assertThat(this.resolution, containsString(resolution));
        return this;
    }

    @Override
    public ExecutionFailure assertHasNoCause(String description) {
        Matcher<String> matcher = containsString(description);
        for (String cause : causes) {
            if (matcher.matches(cause)) {
                failureOnUnexpectedOutput(String.format("Expected no failure with description '%s', found: %s", description, cause));
            }
        }
        return this;
    }

    @Override
    public ExecutionFailure assertHasNoCause() {
        assertThat(causes, isEmpty());
        return this;
    }

    @Override
    public ExecutionFailure assertHasDescription(String context) {
        assertThatDescription(startsWith(context));
        return this;
    }

    @Override
    public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
        for (String description : descriptions) {
            if (matcher.matches(description)) {
                return this;
            }
        }
        failureOnUnexpectedOutput(String.format("No matching failure description found in %s", descriptions));
        return this;
    }

    @Override
    public ExecutionFailure assertTestsFailed() {
        new DetailedExecutionFailure(this).assertTestsFailed();
        return this;
    }

    @Override
    public DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
        return new DependencyResolutionFailure(this, configurationPath);
    }

    private static class Problem {
        final String description;
        final List<String> causes;

        private Problem(String description, List<String> causes) {
            this.description = description;
            this.causes = causes;
        }
    }
}
