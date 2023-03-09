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
    boolean isSatisfied() throws Exception;

    static boolean doSatisfies(Class<? extends TestPrecondition> preconditionClass) throws Exception {
        final TestPrecondition precondition = preconditionClass
            .getDeclaredConstructor()
            .newInstance();
        return precondition.isSatisfied();
    }

    static boolean doSatisfiesAll(Class<? extends TestPrecondition> preconditionClasses[]) throws Exception {
        for (Class<? extends TestPrecondition> preconditionClass : preconditionClasses) {
            if (!doSatisfies(preconditionClass)) {
                return false;
            }
        }
        return true;
    }

    static boolean notSatisfies(Class<? extends TestPrecondition> preconditionClass) throws Exception {
        return !doSatisfies(preconditionClass);
    }
}

