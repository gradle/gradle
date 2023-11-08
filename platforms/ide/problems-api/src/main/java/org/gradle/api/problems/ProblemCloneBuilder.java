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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.api.problems.locations.ProblemLocation;

import java.util.List;
import java.util.Map;

/**
 * A builder for creating a {@link Problem} instance from an existing Problem.
 * The values will be initialized with the values of the given problem.
 *
 * @since 8.6
 */
@Incubating
public interface ProblemCloneBuilder extends BuildableProblemBuilder,
    ProblemBuilderDefiningDocumentation,
    ProblemBuilderDefiningLocation,
    ProblemBuilderDefiningLabel,
    ProblemBuilderDefiningCategory {
    List<String> getSolutions();

    List<ProblemLocation> getLocations();

    Map<String, String> getAdditionalData();
}
