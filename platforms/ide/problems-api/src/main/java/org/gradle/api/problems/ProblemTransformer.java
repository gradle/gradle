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

/**
 * Describes a transformation that can be applied to a problem.
 * <p>
 * These transformers could be added to the {@link Problems} service to transform problems before they are reported.
 *
 * @since 8.5
 */
@FunctionalInterface
@Incubating
public interface ProblemTransformer {

    /**
     * Transforms the given problem. The returned problem will be reported instead of the original problem.
     * <p>
     * Transformations do not need to create a new problem, they can also modify the given problem.
     *
     * @param problem the problem to transform
     * @return the transformed problem
     *
     * @since 8.5
     */
    Problem transform(Problem problem);

}
