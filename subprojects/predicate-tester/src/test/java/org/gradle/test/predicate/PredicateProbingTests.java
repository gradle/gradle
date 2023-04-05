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

package org.gradle.test.predicate;

import org.gradle.test.fixtures.condition.PredicatesFile;
import org.gradle.test.precondition.TestPrecondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * This class executes a test, checking all the allowed precondition combinations.
 *
 * <p>
 * Each combination can have three outcomes:
 * <ul>
 *     <li><b>Successful:</b> the precondition combination could be satisfied.</li>
 *     <li>
 *         <b>Skipped:</b> the precondition combination could <i>not</i> be satisfied.
 *         This is normal, and simply signifies that the predicate is not satisfied on the current system
 *     </li>
 *     <li>
 *         <b>Failed:</b> there was an error meanwhile checking the combination.
 *         This shouldn't be normal, and needs fixing.
 *     </li>
 * </ul>
 */
class PredicateProbingTests {

    public static final String VALID_PRECONDITION_COMBINATIONS_CSV = "/valid-precondition-combinations.csv";

    private static List<Class<?>> loadClasses(String[] classNames) {
        return Arrays.stream(classNames).map(
            className -> {
                try {
                    return Class.forName("org.gradle.test.preconditions." + className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        ).collect(Collectors.toList());
    }

    private static Stream<Arguments> validPreconditionCombinationClassSource() {
        return PredicatesFile.streamCombinationsFile(VALID_PRECONDITION_COMBINATIONS_CSV)
            .map(PredicateProbingTests::loadClasses)
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("validPreconditionCombinationClassSource")
    void test(List<Class<? extends TestPrecondition>> testClasses) throws Exception {
        boolean satisfied = true;

        for (Class<?> clazz : testClasses) {
            final TestPrecondition precondition = (TestPrecondition) clazz.getDeclaredConstructor().newInstance();
            System.out.println(
                String.format("Testing '%s'", clazz.getName())
            );
            satisfied &= precondition.isSatisfied();
        }

        assumeTrue(satisfied);

    }

}
