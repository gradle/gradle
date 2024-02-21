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
import org.gradle.tooling.events.ProgressEvent;

/**
 * An event holding a {@link BaseProblemDescriptor}.
 *
 * There are 2 sub types of a @link BaseProblemDescriptor}:
 *<pre>
 *  - {@link ProblemDescriptor} - describes a single problem that occurred during the build
 *  - {@link ProblemAggregationDescriptor} - describes aggregated problems that happened more than once during the build
 *</pre>
 * @since 8.4
 */
@NonNullApi
@Incubating
public interface ProblemEvent extends ProgressEvent {
    @Override
    BaseProblemDescriptor getDescriptor();
}
