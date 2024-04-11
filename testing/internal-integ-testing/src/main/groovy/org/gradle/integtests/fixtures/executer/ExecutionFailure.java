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

import java.util.function.Consumer;

public interface ExecutionFailure extends ExecutionResult {
    /**
     * {@inheritDoc}
     */
    @Override
    ExecutionFailure getIgnoreBuildSrc();

    ExecutionFailure assertHasLineNumber(int lineNumber);

    ExecutionFailure assertHasFileName(String filename);

    /**
     * Asserts that the given number of failures are present.
     */
    ExecutionFailure assertHasFailures(int count);

    /**
     * Assert that there is a failure present with the given description and invokes the given action on the failure.
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     *
     * @return this
     */
    ExecutionFailure assertHasFailure(String description, Consumer<? super Failure> action);

    /**
     * Asserts that there is a failure present with the given cause (ie the bit after the description).
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     */
    ExecutionFailure assertHasCause(String cause);

    /**
     * Asserts that there is a failure present with the given cause (ie the bit after the description).
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     * They are also required to contain a link to documentation.
     */
    default ExecutionFailure assertHasDocumentedCause(String cause) {
        return assertHasCause(DocumentationUtils.normalizeDocumentationLink(cause));
    }

    /**
     * Asserts that there is a failure present with the given cause (ie the bit after the description).
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     */
    ExecutionFailure assertThatCause(Matcher<? super String> matcher);

    /**
     * Asserts that there is a failure present with the given description (ie the bit after '* What went wrong').
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     */
    ExecutionFailure assertHasDescription(String description);

    /**
     * Asserts that there is a failure present with the given description (ie the bit after '* What went wrong').
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     */
    ExecutionFailure assertThatDescription(Matcher<? super String> matcher);

    /**
     * Asserts that there all failures match the given description (ie the bit after '* What went wrong').
     *
     * <p>Error messages are normalized to use new-line char as line separator.
     */
    ExecutionFailure assertThatAllDescriptions(Matcher<? super String> matcher);

    /**
     * Asserts that the reported failure has exactly the given resolutions (ie the bit after '* Try').
     */
    ExecutionFailure assertHasResolutions(String... resolutions);

    /**
     * Asserts that the reported failure has the given resolution, and maybe more resolutions.
     */
    ExecutionFailure assertHasResolution(String resolution);

    /**
     * Asserts that there is no exception that <em>contains</em> the given description.
     */
    ExecutionFailure assertHasNoCause(String description);

    ExecutionFailure assertHasNoCause();

    ExecutionFailure assertTestsFailed();

    /**
     * @param configurationPath, for example ':compile'
     */
    DependencyResolutionFailure assertResolutionFailure(String configurationPath);

    interface Failure {
        /**
         * Asserts that this failure has the given number of direct causes.
         */
        void assertHasCauses(int count);

        /**
         * Asserts that this failure has the given cause
         */
        void assertHasCause(String message);

        /**
         * Asserts that this failure has the given first cause
         */
        void assertHasFirstCause(String message);
    }
}
