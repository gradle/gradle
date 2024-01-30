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

package org.gradle.internal.component.resolution.failure;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactTransformFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousGraphVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleArtifactVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleGraphVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleRequestedConfigurationFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.InvalidMultipleVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.UnknownArtifactSelectionFailureDescriber;
import org.gradle.internal.component.resolution.failure.failuretype.ResolutionFailure;
import org.gradle.internal.instantiation.InstantiatorFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FailureDescriberRegistry {
    private final InstantiatorFactory instantiatorFactory;
    private final DocumentationRegistry documentationRegistry;
    private final Map<Class<? extends ResolutionFailure>, List<ResolutionFailureDescriber<?, ? extends ResolutionFailure>>> describers = new HashMap<>();

    private FailureDescriberRegistry(InstantiatorFactory instantiatorFactory, DocumentationRegistry documentationRegistry) {
        this.instantiatorFactory = instantiatorFactory;
        this.documentationRegistry = documentationRegistry;
    }

    public static FailureDescriberRegistry emptyRegistry(InstantiatorFactory instantiatorFactory, DocumentationRegistry documentationRegistry) {
        return new FailureDescriberRegistry(instantiatorFactory, documentationRegistry);
    }

    public static FailureDescriberRegistry standardRegistry(InstantiatorFactory instantiatorFactory, DocumentationRegistry documentationRegistry) {
        FailureDescriberRegistry registry = new FailureDescriberRegistry(instantiatorFactory, documentationRegistry);

        registry.registerDescriber(AmbiguousGraphVariantsFailureDescriber.class);
        registry.registerDescriber(IncompatibleGraphVariantsFailureDescriber.class);

        registry.registerDescriber(AmbiguousArtifactVariantsFailureDescriber.class);
        registry.registerDescriber(IncompatibleArtifactVariantsFailureDescriber.class);
        registry.registerDescriber(InvalidMultipleVariantsFailureDescriber.class);
        registry.registerDescriber(AmbiguousArtifactTransformFailureDescriber.class);

        registry.registerDescriber(IncompatibleRequestedConfigurationFailureDescriber.class);
        registry.registerDescriber(UnknownArtifactSelectionFailureDescriber.class);

        return registry;
    }

    public <FAILURE extends ResolutionFailure> List<ResolutionFailureDescriber<?, FAILURE>> getDescribers(FAILURE failure) {
        List<ResolutionFailureDescriber<?, FAILURE>> result = new ArrayList<>();
        describers.getOrDefault(failure.getClass(), Collections.emptyList()).forEach(d -> {
            @SuppressWarnings("unchecked") ResolutionFailureDescriber<?, FAILURE> typedDescriber = (ResolutionFailureDescriber<?, FAILURE>) d;
            result.add(typedDescriber);
        });
        return result;
    }

    public void registerDescriber(Class<? extends ResolutionFailureDescriber<?, ?>> describerClass) {
        ResolutionFailureDescriber<?, ?> describer = instantiatorFactory.inject().newInstance(describerClass, documentationRegistry);
        Class<? extends ResolutionFailure> describedFailureType = describer.getDescribedFailureType();
        describers.computeIfAbsent(describedFailureType, k -> new ArrayList<>()).add(describer);
    }
}
