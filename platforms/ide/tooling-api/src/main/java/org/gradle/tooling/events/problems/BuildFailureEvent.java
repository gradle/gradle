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
package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;

import java.util.List;

/**
 *
 * Represents build failures and their associated problems.
 *
 * @since 8.12
 */
@NonNullApi
@Incubating
public interface BuildFailureEvent extends ProblemEvent {

    /**
     * Returns the list of failures that caused the build to fail.
     *
     * @return the list of failures
     * @since 8.12
     */
    List<Failure> getFailures();
}
