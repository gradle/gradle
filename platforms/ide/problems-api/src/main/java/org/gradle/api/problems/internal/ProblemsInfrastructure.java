/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.jspecify.annotations.Nullable;

public class ProblemsInfrastructure {
    private final IsolatableToBytesSerializer isolatableSerializer;
    @Nullable
    private final ProblemStream problemStream;
    private final AdditionalDataBuilderFactory additionalDataBuilderFactory;
    private final Instantiator instantiator;
    private final PayloadSerializer payloadSerializer;
    private final IsolatableFactory isolatableFactory;

    public ProblemsInfrastructure(
        AdditionalDataBuilderFactory additionalDataBuilderFactory,
        Instantiator instantiator,
        PayloadSerializer payloadSerializer,
        IsolatableFactory isolatableFactory,
        IsolatableToBytesSerializer isolatableSerializer,
        @Nullable
        ProblemStream problemStream
    ) {
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
        this.instantiator = instantiator;
        this.payloadSerializer = payloadSerializer;
        this.isolatableFactory = isolatableFactory;
        this.isolatableSerializer = isolatableSerializer;
        this.problemStream = problemStream;
    }

    public IsolatableToBytesSerializer getIsolatableSerializer() {
        return isolatableSerializer;
    }

    @Nullable
    public ProblemStream getProblemStream() {
        return problemStream;
    }

    public AdditionalDataBuilderFactory getAdditionalDataBuilderFactory() {
        return additionalDataBuilderFactory;
    }

    public Instantiator getInstantiator() {
        return instantiator;
    }

    public PayloadSerializer getPayloadSerializer() {
        return payloadSerializer;
    }

    public IsolatableFactory getIsolatableFactory() {
        return isolatableFactory;
    }
}
