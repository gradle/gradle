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

package org.gradle.api.problems.internal;

/**
 * Interface for describing structured information about a problem.
 */
public interface ProblemReport {

    /**
     * Returns the problem definition, i.e. the data that is independent of the report context.
     */
    ProblemDefinition getDefinition();

    /**
     * Returns the contextual information associated with this problem.
     */
    ProblemContext getContext();

    /**
     * Returns a problem builder with fields initialized with values from this instance.
     */
    InternalProblemBuilder toBuilder();
}
