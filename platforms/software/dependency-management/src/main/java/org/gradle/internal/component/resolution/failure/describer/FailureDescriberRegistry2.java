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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.component.resolution.failure.failures.ResolutionFailure2;
import org.gradle.internal.instantiation.InstantiatorFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FailureDescriberRegistry2 {
    private final InstantiatorFactory instantiatorFactory;
    private final DocumentationRegistry documentationRegistry;
    private final Map<Class<? extends ResolutionFailure2>, List<ResolutionFailureDescriber2<?, ? extends ResolutionFailure2>>> describers = new HashMap<>();

    public FailureDescriberRegistry2(InstantiatorFactory instantiatorFactory, DocumentationRegistry documentationRegistry) {
        this.instantiatorFactory = instantiatorFactory;
        this.documentationRegistry = documentationRegistry;
    }

    public <FAILURE extends ResolutionFailure2> List<ResolutionFailureDescriber2<?, FAILURE>> getDescribers(FAILURE failure) {
        List<ResolutionFailureDescriber2<?, FAILURE>> result = new ArrayList<>();
        describers.getOrDefault(failure.getClass(), Collections.emptyList()).forEach(d -> {
            @SuppressWarnings("unchecked") ResolutionFailureDescriber2<?, FAILURE> typedDescriber = (ResolutionFailureDescriber2<?, FAILURE>) d;
            result.add(typedDescriber);
        });
        return result;
    }

    public void registerDescriber(Class<? extends ResolutionFailureDescriber2<?, ?>> describerClass) {
        ResolutionFailureDescriber2<?, ?> describer = instantiatorFactory.inject().newInstance(describerClass, documentationRegistry);
        Class<? extends ResolutionFailure2> describedFailureType = describer.getDescribedFailureType();
        describers.computeIfAbsent(describedFailureType, k -> new ArrayList<>()).add(describer);
    }
}
