/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution

import groovy.transform.CompileStatic

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

@CompileStatic
class WorkValidationExceptionChecker {
    private final WorkValidationException error
    private final Set<String> verified
    private final Set<String> problems

    static void check(Exception exception, boolean ignoreType = false, @DelegatesTo(value = WorkValidationExceptionChecker, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        assert exception instanceof WorkValidationException
        def checker = new WorkValidationExceptionChecker(exception, ignoreType)
        spec.delegate = checker
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        checker.done()
    }

    private WorkValidationExceptionChecker(WorkValidationException ex, boolean ignoreType) {
        error = ex
        verified = [] as Set
        problems = error.problems.collect {
            // assertions do not verify the type name
            normaliseLineSeparators(ignoreType ? it.substring(it.indexOf("' ") + 2).capitalize() : it.capitalize())
        } as Set
    }

    void messageContains(String expected) {
        String actualMessage = normaliseLineSeparators(error.message.trim())
        String expectedMessage = normaliseLineSeparators(expected.trim())
        assert actualMessage.contains(expectedMessage)
    }

    void hasMessage(String expected) {
        String actualError = normaliseLineSeparators(error.message.trim())
        String expectedError = normaliseLineSeparators(expected.trim())
        assert actualError == expectedError
    }

    void hasProblem(String problem) {
        problem = problem.capitalize()
        assert problems.contains(problem)
        verified.add(problem)
    }

    private void done() {
        if (verified && !(verified == problems as Set)) {
            throw new AssertionError("Expected ${problems.size()} problems but you only checked ${verified.size()}")
        }
    }
}
