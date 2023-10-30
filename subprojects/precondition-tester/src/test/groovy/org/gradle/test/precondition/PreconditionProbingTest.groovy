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

package org.gradle.test.precondition


import spock.lang.Specification
import org.junit.jupiter.api.Assumptions

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
abstract class PreconditionProbingTest extends Specification {

    private static Class<? extends TestPrecondition> loadClass(String className) {
        try {
            return Class.forName(className) as Class<? extends TestPrecondition>
        } catch (ClassNotFoundException ex) {
            final String message = String.format(
                "Class '%s' cannot be found. You might be missing a dependency in 'subprojects/predicate-tester/builds.gradle.kts'?",
                className
            )
            throw new RuntimeException(message, ex)
        }
    }

    def "Preconditions #classNames"() {
        given:
        def classes = classNames.collect(PreconditionProbingTest::loadClass)

        expect:
        for (def precondition : classes) {
            def preconditionInstance = precondition.getDeclaredConstructor().newInstance()
            Assumptions.assumeTrue(preconditionInstance.satisfied)
        }

        where:
        classNames << PredicatesFile.DEFAULT_ACCEPTED_COMBINATIONS
    }

}
