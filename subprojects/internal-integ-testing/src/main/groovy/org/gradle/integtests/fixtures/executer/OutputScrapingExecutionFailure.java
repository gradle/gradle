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

import org.gradle.util.TextUtil;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.gradle.util.Matchers.isEmpty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class OutputScrapingExecutionFailure extends OutputScrapingExecutionResult implements ExecutionFailure {
    private static final Pattern FAILURE_PATTERN = Pattern.compile("(?m)FAILURE: .+$");
    private static final Pattern CAUSE_PATTERN = Pattern.compile("(?m)(^\\s*> )");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?ms)^\\* What went wrong:$(.+?)^\\* Try:$");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?ms)^\\* Where:((.+)'.+') line: (\\d+)$");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("(?ms)^\\* Try:$(.+?)^\\* Exception is:$");
    private final String description;
    private final String lineNumber;
    private final String fileName;
    private final String resolution;
    private final List<String> causes = new ArrayList<String>();

    public OutputScrapingExecutionFailure(String output, String error) {
        super(output, error);

        java.util.regex.Matcher matcher = FAILURE_PATTERN.matcher(error);
        if (matcher.find()) {
            if (matcher.find()) {
                throw new AssertionError("Found multiple failure sections in build error output.");
            }
        }

        matcher = LOCATION_PATTERN.matcher(error);
        if (matcher.find()) {
            fileName = matcher.group(1).trim();
            lineNumber = matcher.group(3);
        } else {
            fileName = "";
            lineNumber = "";
        }

        matcher = DESCRIPTION_PATTERN.matcher(error);
        if (matcher.find()) {
            String problemStr = matcher.group(1);
            Problem problem = extract(problemStr);
            description = problem.description;
            causes.addAll(problem.causes);
            while (matcher.find()) {
                problemStr = matcher.group(1);
                problem = extract(problemStr);
                causes.addAll(problem.causes);
            }
        } else {
            description = "";
        }

        matcher = RESOLUTION_PATTERN.matcher(error);
        if (!matcher.find()) {
            resolution = "";
        } else {
            resolution = matcher.group(1).trim();
        }
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

    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        assertThat(this.lineNumber, equalTo(String.valueOf(lineNumber)));
        return this;
    }

    public ExecutionFailure assertHasFileName(String filename) {
        assertThat(this.fileName, equalTo(filename));
        return this;
    }

    public ExecutionFailure assertHasCause(String description) {
        assertThatCause(startsWith(description));
        return this;
    }

    public ExecutionFailure assertThatCause(Matcher<String> matcher) {
        for (String cause : causes) {
            if (matcher.matches(cause)) {
                return this;
            }
        }
        fail(String.format("No matching cause found in %s", causes));
        return this;
    }

    public ExecutionFailure assertHasResolution(String resolution) {
        assertThat(this.resolution, equalTo(resolution));
        return this;
    }

    public ExecutionFailure assertHasNoCause() {
        assertThat(causes, isEmpty());
        return this;
    }

    public ExecutionFailure assertHasDescription(String context) {
        assertThatDescription(equalTo(context));
        return this;
    }

    public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
        assertThat(description, matcher);
        return this;
    }

    public ExecutionFailure assertTestsFailed() {
        new DetailedExecutionFailure(this).assertTestsFailed();
        return this;
    }

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