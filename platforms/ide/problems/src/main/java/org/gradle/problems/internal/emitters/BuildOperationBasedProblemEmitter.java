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

package org.gradle.problems.internal.emitters;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Emits problems as build operation progress events.
 *
 * @since 8.6
 */
@Incubating
public class BuildOperationBasedProblemEmitter implements ProblemEmitter {

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final Instantiator instantiator;
    private final PayloadSerializer payloadSerializer;

    public BuildOperationBasedProblemEmitter(BuildOperationProgressEventEmitter eventEmitter, Instantiator instantiator, PayloadSerializer payloadSerializer) {
        this.eventEmitter = eventEmitter;
        this.instantiator = instantiator;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void emit(InternalProblem problem, @Nullable OperationIdentifier id) {
        Class<? extends AdditionalData> additionalDataType = problem.getAdditionalDataType();
        if (additionalDataType != null) {
            problem = buildProblemWithIsolatedAdditionalData(problem, additionalDataType);
        }
        eventEmitter.emitNow(id, new DefaultProblemProgressDetails(problem));
    }

    @Nonnull
    private InternalProblem buildProblemWithIsolatedAdditionalData(InternalProblem problem, Class<? extends AdditionalData> additionalDataType) {
        AdditionalData additionalDataInstance = instantiator.newInstance(additionalDataType);
        List<Action<? super AdditionalData>> additionalDataConfigs = problem.getAdditionalDataConfigs();
        for (Action<? super AdditionalData> action : additionalDataConfigs) {
            action.execute(additionalDataInstance);
        }

        InternalProblemBuilder builder = problem.toBuilder(null, instantiator, payloadSerializer);
        builder.additionalDataInternal(additionalDataInstance);
        return builder.build();
    }
}
