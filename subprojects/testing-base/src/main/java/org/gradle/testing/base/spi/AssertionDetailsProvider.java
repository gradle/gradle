/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.base.spi;

import org.gradle.api.Incubating;

/**
 * Extracts expected and actual values from exceptions representing test assertion failures.
 * <p>
 * Custom implementations are discovered in the test JVM via ServiceLoader. This enables custom execution environments (e.g. IDEs) to create detailed test operation events for custom test assertion
 * failures, ultimately exposing expected and actual values via the Tooling API. See {@code org.gradle.tooling.TestAssertionFailure} for details.
 *
 * @since 7.6
 */
@Incubating
public interface AssertionDetailsProvider {

    /**
     * Returns {@code true} if the exception class name is recognized by this provider. If that's the case, {@link #extractAssertionDetails(Throwable)} should return a non-null result.
     *
     * @param className The class name of the object that's going to be passed to {@link #extractAssertionDetails(Throwable)} if the return value is {@code true}.
     * @return Whether this provider recognizes the argument as an assertion failure.
     */
    boolean isAssertionType(String className);

    /**
     * Returns the expected and actual values from the target assertion failure.
     * <p>
     * This method will be only called for types which {@link #isAssertionType(String)} returns true. The return value should be non-null in that case. At the same time, the fields in
     * {@link AssertionFailureDetails} are nulleble, because it's possible that a provider recognizes the target exception as a test assertion failure but the expected and actual values are not
     * available.
     * <p>
     * The implementation should only analyze the current class only ignoring the type hierarchy, as the providers will be invoked for each superclass from the bottom up.
     *
     * @param t The failure to extract information from.
     * @return The data class holding the expected and actual values.
     */
    AssertionFailureDetails extractAssertionDetails(Throwable t);
}
