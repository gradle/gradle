/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Provides common assertions for querying task order.
 *
 * An 'any' rule asserts that all of the specified tasks occur in any order.
 *
 * any(':a', ':b', ':c') would match on any permutation of ':a', ':b', ':c'.
 *
 * An 'exact' rule asserts that all of the specified tasks occur in the order
 * provided.  Note that other tasks may appear - it only verifies that the
 * given tasks occur in order.
 *
 * exact(':b', ':d') would match on [ ':b', ':d' ] or say [ ':a', ':b', ':c', ':d' ]
 *
 * Assertions can also be nested:
 *
 * exact(':a', any(':b', ':c'), ':d') would match any of the following:
 *   - [ ':a', ':b', ':c', ':d' ]
 *   - [ ':a', ':c', ':b', ':d' ]
 *   but not
 *   - [ ':b', ':a', ':c', ':d' ]
 *
 * Similarly, an exact rule can be nested inside of an any rule:
 *
 * any(':a', exact(':b', ':c)) would match any of the following:
 *   - [ ':a', ':b', ':c' ]
 *   - [ ':b', ':c', ':a' ]
 *   - [ ':b', ':a', ':c' ]
 *   but not
 *   - [ ':c', ':a', ':b' ]
 *   - [ ':a', ':c', ':b' ]
 *   or any other combination where :c occurs before :b
 */
public class TaskOrderSpecs {

    public static TaskOrderSpec any(Object[] contraints) {
        return new AnyOrderSpec(Arrays.asList(contraints));
    }

    public static TaskOrderSpec exact(Object[] constraints) {
        List<Object> flattenedConstraints = GUtil.flatten(constraints, new ArrayList<Object>());
        return new ExactOrderSpec(flattenedConstraints);
    }

    private static abstract class RecursiveOrderSpec implements TaskOrderSpec {
        protected final List<Object> constraints;

        public RecursiveOrderSpec(List<Object> constraints) {
            this.constraints = constraints;
        }

        protected int checkConstraint(Object constraint, int lastIndex, List<String> executedTaskPaths) {
            int index;
            if (constraint instanceof String) {
                index = executedTaskPaths.indexOf(constraint);
            } else if (constraint instanceof TaskOrderSpec) {
                index = ((TaskOrderSpec)constraint).assertMatches(lastIndex, executedTaskPaths);
            } else {
                throw new IllegalArgumentException();
            }
            assert index > lastIndex : String.format("%s does not occur in expected order (expected: %s, actual %s)", constraint, this.toString(), executedTaskPaths);
            return index;
        }

        @Override
        public Set<String> getTasks() {
            Set<String> tasks = Sets.newHashSet();
            for (Object constraint : constraints) {
                if (constraint instanceof String) {
                    tasks.add((String) constraint);
                } else if (constraint instanceof TaskOrderSpec) {
                    tasks.addAll(((TaskOrderSpec) constraint).getTasks());
                } else {
                    throw new IllegalArgumentException();
                }
            }
            return tasks;
        }

        abstract String getDisplayName();

        @Override
        public String toString() {
            return String.format("%s(%s)", getDisplayName(), constraints);
        }
    }

    private static class AnyOrderSpec extends RecursiveOrderSpec {
        public AnyOrderSpec(List<Object> constraints) {
            super(constraints);
        }

        @Override
        public int assertMatches(int lastIndex, List<String> executedTaskPaths) {
            int highestIndex = lastIndex;
            for (Object constraint : constraints) {
                int index = checkConstraint(constraint, lastIndex, executedTaskPaths);
                highestIndex = index > highestIndex ? index : highestIndex;
            }
            return highestIndex;
        }

        @Override
        String getDisplayName() {
            return "any";
        }
    }

    private static class ExactOrderSpec extends RecursiveOrderSpec {
        public ExactOrderSpec(List<Object> constraints) {
            super(constraints);
        }

        @Override
        public int assertMatches(int lastIndex, List<String> executedTaskPaths) {
            for (Object constraint : constraints) {
                lastIndex = checkConstraint(constraint, lastIndex, executedTaskPaths);
            }
            return lastIndex;
        }

        @Override
        String getDisplayName() {
            return "exact";
        }
    }
}
