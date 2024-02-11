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
package org.gradle.test.precondition;

/**
 * Usage:
 * <pre>
 * <code>@</code>Requires(TestPrecondition.JDK17_OR_LATER)
 * def "test with environment expectations"() {
 *     // the test is executed with Java 17 or later
 * }
 * </pre>
 *
 * @see Requires
 */
public interface TestPrecondition {

    /**
     * Returns true if the precondition is satisfied.
     * @throws Exception if the precondition cannot be checked
     */
    boolean isSatisfied() throws Exception;

    /**
     * Utility method to check if a precondition class is satisfied.
     *
     * @param preconditionClass the class of the precondition to be checked
     * @return true if the precondition is satisfied
     * @throws Exception if the precondition cannot be checked
     */
    static boolean satisfied(Class<? extends TestPrecondition> preconditionClass) throws Exception {
        final TestPrecondition precondition = preconditionClass
            .getDeclaredConstructor()
            .newInstance();
        return precondition.isSatisfied();
    }

    /**
     * Utility method to check if all precondition classes are satisfied.
     *
     * @param preconditionClasses a list of precondition classes to be checked
     * @return true if all preconditions are satisfied
     * @throws Exception if a precondition cannot be checked
     */
    static boolean allSatisfied(Class<? extends TestPrecondition>[] preconditionClasses) throws Exception {
        for (Class<? extends TestPrecondition> preconditionClass : preconditionClasses) {
            if (!satisfied(preconditionClass)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Utility method to check if a precondition class is <i>not</i> satisfied.
     * @param preconditionClass the class of the precondition to be checked
     * @return true if the precondition is <i>not</i> satisfied
     */
    static boolean notSatisfied(Class<? extends TestPrecondition> preconditionClass) throws Exception {
        return !satisfied(preconditionClass);
    }
}

