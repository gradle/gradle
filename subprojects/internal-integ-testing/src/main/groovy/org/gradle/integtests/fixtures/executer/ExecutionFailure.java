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
package org.gradle.integtests.fixtures.executer;

import org.hamcrest.Matcher;

public interface ExecutionFailure extends ExecutionResult {
    ExecutionFailure assertHasLineNumber(int lineNumber);

    ExecutionFailure assertHasFileName(String filename);

    /**
     * Asserts that the reported failure has the given cause (ie the bit after the description)
     */
    ExecutionFailure assertHasCause(String description);

    /**
     * Asserts that the reported failure has the given cause (ie the bit after the description)
     */
    ExecutionFailure assertThatCause(Matcher<String> matcher);

    /**
     * Asserts that the reported failure has the given description (ie the bit after '* What went wrong').
     */
    ExecutionFailure assertHasDescription(String context);

    /**
     * Asserts that the reported failure has the given description (ie the bit after '* What went wrong').
     */
    ExecutionFailure assertThatDescription(Matcher<String> matcher);

    /**
     * Asserts that the reported failure has the given resolution (ie the bit after '* Try').
     */
    ExecutionFailure assertHasResolution(String resolution);

    ExecutionFailure assertHasNoCause();

    ExecutionFailure assertTestsFailed();

    /**
     * @param configurationPath, for example ':compile'
     */
    DependencyResolutionFailure assertResolutionFailure(String configurationPath);
}
