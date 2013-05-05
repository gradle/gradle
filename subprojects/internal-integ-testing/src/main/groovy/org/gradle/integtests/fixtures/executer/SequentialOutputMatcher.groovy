/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.integtests.fixtures.executer

import org.gradle.internal.SystemProperties
import org.junit.Assert
import org.gradle.util.TextUtil

/**
 * Check that the actual output lines match the expected output lines in content and order.
 */
class SequentialOutputMatcher {
    private static final String NL = SystemProperties.lineSeparator

    public void assertOutputMatches(String expected, String actual, boolean ignoreExtraLines) {
        List actualLines = normaliseOutput(actual.readLines())
        List expectedLines = expected.readLines()
        assertOutputLinesMatch(expectedLines, actualLines, ignoreExtraLines, actual)
    }

    protected void assertOutputLinesMatch(List<String> expectedLines, List<String> actualLines, boolean ignoreExtraLines, String actual) {
        int pos = 0
        for (; pos < actualLines.size() && pos < expectedLines.size(); pos++) {
            String expectedLine = expectedLines[pos]
            String actualLine = actualLines[pos]
            boolean matches = compare(expectedLine, actualLine)
            if (!matches) {
                if (expectedLine.contains(actualLine)) {
                    Assert.fail("Missing text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
                }
                if (actualLine.contains(expectedLine)) {
                    Assert.fail("Extra text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
                }
                Assert.fail("Unexpected value at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
            }
        }
        if (pos == actualLines.size() && pos < expectedLines.size()) {
            Assert.fail("Lines missing from actual result, starting at line ${pos + 1}.${NL}Expected: ${expectedLines[pos]}${NL}Actual output:${NL}$actual${NL}---")
        }
        if (!ignoreExtraLines && pos < actualLines.size() && pos == expectedLines.size()) {
            Assert.fail("Extra lines in actual result, starting at line ${pos + 1}.${NL}Actual: ${actualLines[pos]}${NL}Actual output:${NL}$actual${NL}---")
        }
    }

    private List<String> normaliseOutput(List<String> lines) {
        if (lines.empty) {
            return lines;
        }
        List<String> result = new ArrayList<String>()
        for (String line : lines) {
            if (line.matches('Download .+')) {
                // ignore
            } else {
                result << line
            }
        }
        return result
    }

    protected boolean compare(String expected, String actual) {
        if (actual == expected) {
            return true
        }

        if (expected == 'Total time: 1 secs') {
            return actual.matches('Total time: .+ secs')
        }

        // Normalise default object toString() values
        actual = actual.replaceAll('(\\w+(\\.\\w+)*)@\\p{XDigit}+', '$1@12345')
        // Normalise file separators
        actual = TextUtil.normaliseFileSeparators(actual)

        return actual == expected
    }
}
