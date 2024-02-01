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

import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactTransformFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.AmbiguousGraphVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ConfigurationNotConsumableFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ExternalRequestedConfigurationNotFoundFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleArtifactVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleGraphVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.IncompatibleRequestedConfigurationFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.InvalidMultipleVariantsFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.NoMatchingCapabilitiesFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.RequestedConfigurationNotFoundFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.describer.UnknownArtifactSelectionFailureDescriber;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformFailure;
import org.gradle.internal.component.resolution.failure.type.AmbiguousResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotConsumableFailure;
import org.gradle.internal.component.resolution.failure.type.ExternalRequestedConfigurationNotFoundFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleRequestedConfigurationFailure;
import org.gradle.internal.component.resolution.failure.type.IncompatibleResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.InvalidMultipleVariantsSelectionFailure;
import org.gradle.internal.component.resolution.failure.type.NoMatchingCapabilitiesFailure;
import org.gradle.internal.component.resolution.failure.type.RequestedConfigurationNotFoundFailure;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure;
import org.gradle.internal.component.resolution.failure.type.VariantAwareAmbiguousResolutionFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class ResolutionFailureDescriberRegistry {
    private final ObjectFactory objectFactory;
    private final LinkedHashMap<Class<? extends ResolutionFailure>, List<ResolutionFailureDescriber<?, ?>>> describers = new LinkedHashMap<>();

    private ResolutionFailureDescriberRegistry(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public static ResolutionFailureDescriberRegistry emptyRegistry(ObjectFactory objectFactory) {
        return new ResolutionFailureDescriberRegistry(objectFactory);
    }

    public static ResolutionFailureDescriberRegistry standardRegistry(ObjectFactory objectFactory) {
        ResolutionFailureDescriberRegistry registry = new ResolutionFailureDescriberRegistry(objectFactory);

        registry.registerDescriber(VariantAwareAmbiguousResolutionFailure.class, AmbiguousGraphVariantsFailureDescriber.class);
        registry.registerDescriber(IncompatibleGraphVariantFailure.class, IncompatibleGraphVariantsFailureDescriber.class);

        registry.registerDescriber(AmbiguousResolutionFailure.class, AmbiguousArtifactVariantsFailureDescriber.class);
        registry.registerDescriber(IncompatibleResolutionFailure.class, IncompatibleArtifactVariantsFailureDescriber.class);
        registry.registerDescriber(InvalidMultipleVariantsSelectionFailure.class, InvalidMultipleVariantsFailureDescriber.class);
        registry.registerDescriber(AmbiguousArtifactTransformFailure.class, AmbiguousArtifactTransformFailureDescriber.class);

        registry.registerDescriber(IncompatibleRequestedConfigurationFailure.class, IncompatibleRequestedConfigurationFailureDescriber.class);
        registry.registerDescriber(RequestedConfigurationNotFoundFailure.class, RequestedConfigurationNotFoundFailureDescriber.class);
        registry.registerDescriber(ExternalRequestedConfigurationNotFoundFailure.class, ExternalRequestedConfigurationNotFoundFailureDescriber.class);
        registry.registerDescriber(ConfigurationNotConsumableFailure.class, ConfigurationNotConsumableFailureDescriber.class);

        registry.registerDescriber(NoMatchingCapabilitiesFailure.class, NoMatchingCapabilitiesFailureDescriber.class);

        registry.registerDescriber(UnknownArtifactSelectionFailure.class, UnknownArtifactSelectionFailureDescriber.class);

        return registry;
    }

    public <FAILURE extends ResolutionFailure> List<ResolutionFailureDescriber<?, FAILURE>> getDescribers(Class<FAILURE> failureType) {
        List<ResolutionFailureDescriber<?, FAILURE>> result = new ArrayList<>();
        describers.getOrDefault(failureType, Collections.emptyList()).forEach(d -> {
            @SuppressWarnings("unchecked") ResolutionFailureDescriber<?, FAILURE> typedDescriber = (ResolutionFailureDescriber<?, FAILURE>) d;
            result.add(typedDescriber);
        });
        return result;
    }

    public <FAILURE extends ResolutionFailure> void registerDescriber(Class<FAILURE> failureType, Class<? extends ResolutionFailureDescriber<?, FAILURE>> describerType) {
        ResolutionFailureDescriber<?, ?> describer = objectFactory.newInstance(describerType);
        describers.computeIfAbsent(failureType, k -> new ArrayList<>()).add(describer);
    }
}
