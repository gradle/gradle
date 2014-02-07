/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern;

import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

import java.util.ArrayList;
import java.util.List;

public class DefaultPatternMatcher implements Spec<RelativePath> {
    private List<PatternStep> steps;
    private boolean partialMatchDirs;

    public DefaultPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String... patternParts) {
        this.partialMatchDirs = partialMatchDirs;
        steps = new ArrayList<PatternStep>();
        compile(caseSensitive, patternParts);
    }

    private void compile(boolean caseSensitive, String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            steps.add(PatternStepFactory.getStep(parts[i], caseSensitive));
        }
    }

    // segment -> path to test
    // step -> pattern

    public boolean isSatisfiedBy(RelativePath pathToTest) {
        String[] segments = pathToTest.getSegments();
        int nextSegment = 0;
        int nextPattern = 0;
        boolean seenGreedy = false;

        while (nextSegment < segments.length && nextPattern < steps.size()) {
            String currentSegment = segments[nextSegment];
            nextSegment++;
            PatternStep currentPattern = steps.get(nextPattern);
            nextPattern++;

            if (currentPattern.isGreedy()) {
                seenGreedy = true;

                // Find the next non-greedy
                while (nextPattern < steps.size() && steps.get(nextPattern).isGreedy()) {
                    nextPattern++;
                }
                if (nextPattern == steps.size()) {
                    // pattern ends in greedy
                    return true;
                }
                currentPattern = steps.get(nextPattern);
                nextPattern++;

                // advance to the next match and consume it
                while (!currentPattern.matches(currentSegment)) {
                    if (nextSegment == segments.length) {
                        // didn't match, but no more segments to test
                        return partialMatchDirs && !pathToTest.isFile();
                    }
                    currentSegment = segments[nextSegment];
                    nextSegment++;
                }

                // should have match at this point, can continue on around the loop
            } else {
                // not a greedy patternStep
                if (!currentPattern.matches(currentSegment)) {
                    // didn't match, check if we are after another greedy
                    if (seenGreedy) {
                        // rewind pattern to previous greedy and continue
                        nextPattern--;
                        while (!steps.get(nextPattern).isGreedy()) {
                            nextPattern--;
                        }
                        nextSegment--; // back up test by one
                    } else {
                        return false;  // haven't seen greedy, no match
                    }
                }
            }
        }

        // ran out of stuff to test
        if (nextSegment == segments.length && nextPattern == steps.size()) {
            return true;    // if out of pattern too, then it's a match
        }

        if (nextPattern == steps.size() - 1 && steps.get(nextPattern).isGreedy()) {
            return true; // if only a trailing greedy is left, then it matches
        }

        return !pathToTest.isFile() && partialMatchDirs;
    }

    List<PatternStep> getStepsForTest() {
        return steps;
    }
}
