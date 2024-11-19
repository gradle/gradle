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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.AdditionalDataSpec;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;

/**
 * An {@link org.gradle.api.problems.AdditionalDataSpec} that produces {@link ResolutionFailureDataSpec} instances
 * from {@link ResolutionFailure}s.
 */
public interface ResolutionFailureDataSpec extends AdditionalDataSpec {
    /**
     * Creates a new {@link ResolutionFailureDataSpec} instance from the given {@link ResolutionFailure}.
     *
     * @param failure the failure to use as the source of data
     * @return A new instance of a spec used to configure a {@link ResolutionFailureData} instance containing
     * data from the given failure
     */
    ResolutionFailureDataSpec from(ResolutionFailure failure);
}
