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

package org.gradle.tooling.internal.protocol;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.util.List;

@NonNullApi
public interface InternalBasicProblemDetailsVersion3 extends InternalProblemDetailsVersion2 {

    InternalProblemDefinition getDefinition();

    List<InternalSolution> getSolutions();

    @Nullable
    InternalDetails getDetails();

    @Nullable
    InternalContextualLabel getContextualLabel();

    List<InternalLocation> getLocations();

    @Nullable
    InternalFailure getFailure();

    InternalAdditionalData getAdditionalData();
}
