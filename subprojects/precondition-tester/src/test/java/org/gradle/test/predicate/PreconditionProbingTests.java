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

import org.gradle.test.precondition.PredicatesFile;
import org.gradle.test.precondition.TestPrecondition;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * This class executes a special test suite, checking all the allowed precondition combinations.
 *
 * <p>
 * Each combination can have three outcomes:
 * <ul>
 *     <li><b>Successful:</b> the precondition combination could be satisfied.</li>
 *     <li>
 *         <b>Skipped:</b> the precondition combination could <i>not</i> be satisfied.
 *         This is normal, and simply signifies that the precondition is not satisfied on the current system
 *     </li>
 *     <li>
 *         <b>Failed:</b> there was an error meanwhile checking the combination.
 *         This shouldn't be normal, and needs fixing.
 *     </li>
 * </ul>
 */
class PreconditionProbingTests implements ArgumentsProvider {

    private static List<Class<?>> loadClasses(Set<String> classNames) {
        return classNames
            .stream()
            .map(
                className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException ex) {
                        final String message = String.format("Class '%s' cannot be found. You might have a missing the dependency in 'subprojects/predicate-tester/builds.gradle.kts'?", className);
                        throw new RuntimeException(message, ex);
                    }
                }
            ).collect(Collectors.toList());
    }

    private static String getDisplayName(List<? extends Class<?>> classes) {
        return String.format(
            "Preconditions: [%s]",
            classes.stream().map(Class::getSimpleName).collect(Collectors.joining(","))
        );
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        return PredicatesFile.DEFAULT_ACCEPTED_COMBINATIONS
            .stream()
            .map(PreconditionProbingTests::loadClasses)
            .map(classes -> Arguments.of(
                Named.named(getDisplayName(classes), classes)
            ));
    }

    @ParameterizedTest
    @ArgumentsSource(PreconditionProbingTests.class)
    void test(List<Class<? extends TestPrecondition>> testClasses) throws Exception {
        boolean satisfied = true;

        for (Class<?> clazz : testClasses) {
            final TestPrecondition precondition = (TestPrecondition) clazz.getDeclaredConstructor().newInstance();
            System.out.printf("Testing '%s'%n", clazz.getName());
            satisfied &= precondition.isSatisfied();
        }

        assumeTrue(satisfied);
    }

}
