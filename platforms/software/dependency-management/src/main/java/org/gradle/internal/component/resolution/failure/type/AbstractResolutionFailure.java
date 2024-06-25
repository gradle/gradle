/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.type;

import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;

/**
 * An abstract {@link ResolutionFailure} that represents a resolution failure and can provide
 * a {@link ResolutionFailureProblemId} identifying the problem to the Problems API.
 */
public abstract class AbstractResolutionFailure implements ResolutionFailure {
    private final ResolutionFailureProblemId problemId;

    public AbstractResolutionFailure(ResolutionFailureProblemId problemId) {
        this.problemId = problemId;
    }

    @Override
    public ResolutionFailureProblemId getProblemId() {
        return problemId;
    }
}
