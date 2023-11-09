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

package org.gradle.problems.internal;

import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.ProblemTransformer;
import org.gradle.api.problems.ProblemTransformerRegistry;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class DefaultProblemTransformerRegistry implements ProblemTransformerRegistry {

    private final BuildServiceRegistry sharedServices;
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;

    private final Map<Class<? extends ProblemTransformer>, Provider<? extends ProblemTransformer>> registrations = new HashMap<>();


    @Inject
    public DefaultProblemTransformerRegistry(
        Gradle gradle,
        Instantiator instantiator,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory
    ) {
        this.sharedServices = gradle.getSharedServices();
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
    }

    @Override
    public void register(Class<? extends ProblemTransformer> transformerClass) {
        if (registrations.containsKey(transformerClass)) {
            throw new GradleException("Duplicate registration for '" + transformerClass.getName() + "'.");
        }

        Provider<? extends ProblemTransformer> provider = sharedServices.registerIfAbsent(transformerClass.getName(), transformerClass, EMPTY_CONFIGURE_ACTION);
        registrations.put(transformerClass, provider);
    }
}
