/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.problems.GradleProblemGroup;
import org.gradle.api.problems.GradleSecondLevelProblemGroup;

/**
 * Internal view of the {@link GradleProblemGroup Gradle root group}: the sub-groups that are reserved for Gradle's own code
 * and therefore have no getter on the public type.
 */
public interface GradleProblemGroupInternal {

    /**
     * The {@code Deprecation} sub-group. Usage of deprecated Gradle and plugins APIs, features, or behaviors scheduled for
     * removal in a future version. Reserved for the deprecations Gradle reports itself.
     */
    GradleSecondLevelProblemGroup getDeprecation();
}
