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
import org.gradle.tooling.Failure;

import javax.annotation.Nullable;

/**
 * Holds an exception for a problem.
 *
 * @since 8.7
 */
@Incubating
public interface FailureContainer {

    /**
     * Failure that caused the problem.
     * <p>
     * The method will always return <code>null</code> if run against a Gradle version prior to 8.8.
     * @since 8.7
     */
    @Nullable
    Failure getFailure();
}
