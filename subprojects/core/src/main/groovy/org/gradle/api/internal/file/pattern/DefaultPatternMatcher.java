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
import java.util.ListIterator;

public class DefaultPatternMatcher implements Spec<RelativePath> {
    private List<PatternStep> steps;
    private boolean partialMatchDirs;

    public DefaultPatternMatcher(boolean partialMatchDirs, boolean caseSensitive, String... patternParts) {
        this.partialMatchDirs = partialMatchDirs;
        steps = new ArrayList<PatternStep>();
        compile(caseSensitive, patternParts);
    }

    private void compile(boolean caseSensitive, String[] parts) {
        if (parts.length > 0) {
            for (int i = 0; i < parts.length; i++) {
                steps.add(PatternStepFactory.getStep(parts[i], i == parts.length - 1, caseSensitive));
            }
        }
    }

    // segment -> path to test
    // step -> pattern

    public boolean isSatisfiedBy(RelativePath pathToTest) {
        ListIterator<PatternStep> patternIt = steps.listIterator();
        ListIterator<String> testIt = pathToTest.segmentIterator();
        boolean seenGreedy = false;

        PatternStep patternStep;

        while (testIt.hasNext()) {
            String nextToTest = testIt.next();

            if (!patternIt.hasNext()) {
                return false;
            }
            patternStep = patternIt.next();

            if (patternStep.isGreedy()) {
                seenGreedy = true;
                advancePatternStepToNextNonGreedy(patternIt);
                if (!patternIt.hasNext()) {
                    return true;
                }    // pattern ends in greedy
                patternStep = patternIt.next();

                // advance test until match
                while (!(patternStep.matches(nextToTest, !testIt.hasNext() && pathToTest.isFile()) && (
                        (patternIt.hasNext() == testIt.hasNext()) || nextPatternIsGreedy(patternIt)))) {
                    if (!testIt.hasNext()) {
                        return partialMatchDirs && !pathToTest
                                .isFile(); //isTerminatingMatch(pathToTest, patternIt);  // didn't match, but no more segments to test
                    }
                    nextToTest = testIt.next();
                }

                // should have match at this point, can continue on around the loop
            } else {
                // not a greedy patternStep
                if (!patternStep.matches(nextToTest, !testIt.hasNext() && pathToTest.isFile())) {
                    // didn't match, check if we are after another greedy
                    if (seenGreedy) {
                        rewindPatternStepToPreviousGreedy(patternIt);  // rewind pattern to greedy
                        testIt.previous(); // back up test by one
                    } else {
                        return false;  // haven't seen greedy, no match
                    }
                }
            }
        }
        // ran out of stuff to test

        if (!patternIt.hasNext()) {
            return true;    // if out of pattern too, then it's a match
        }

        return isTerminatingMatch(pathToTest, patternIt);
    }

    private boolean nextPatternIsGreedy(ListIterator<PatternStep> patternIt) {
        boolean result = false;
        if (patternIt.hasNext()) {
            PatternStep next = patternIt.next();
            if (next.isGreedy() && !patternIt.hasNext()) {
                result = true;
            }
            patternIt.previous();
        }
        return result;
    }

    private boolean isTerminatingMatch(RelativePath pathToTest, ListIterator<PatternStep> patternIt) {
        PatternStep patternStep;

        if (patternIt.hasNext()) {
            patternStep = patternIt.next();
            if (patternStep.isGreedy() && !patternIt.hasNext()) {
                return true;    // if only a trailing greedy is left, then it matches
            }
        }

        return !pathToTest.isFile() && partialMatchDirs;
    }

    private void advancePatternStepToNextNonGreedy(ListIterator<PatternStep> patternIt) {
        PatternStep next = null;
        while (patternIt.hasNext()) {
            next = patternIt.next();
            if (!next.isGreedy()) {
                break;
            }
        }
        // back up one
        if (next != null && !next.isGreedy()) {
            patternIt.previous();
        }
    }

    private void rewindPatternStepToPreviousGreedy(ListIterator<PatternStep> patternIt) {
        PatternStep result = null;
        while (patternIt.hasPrevious()) {
            result = patternIt.previous();
            if (result.isGreedy()) {
                //patternIt.next();
                return;
            }
        }
        throw new IllegalStateException("PatternStep list iterator in non-greedy state when rewindToLastGreedy");
    }

    List<PatternStep> getStepsForTest() {
        return steps;
    }
}
