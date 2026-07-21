/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.Describable;
import org.gradle.api.GradleException;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

public class SelfDescribingSpec<T> implements Describable, Spec<T> {
    private final Spec<? super T> spec;
    private final Provider<String> descriptionProvider;
    @Nullable
    private String resolvedDescription;

    public SelfDescribingSpec(Spec<? super T> spec, String description) {
        this(spec, Providers.of(description));
    }

    public SelfDescribingSpec(Spec<? super T> spec, Provider<String> descriptionProvider) {
        this.spec = spec;
        this.descriptionProvider = descriptionProvider;
    }

    @Override
    public String getDisplayName() {
        if (resolvedDescription == null) {
            resolvedDescription = descriptionProvider.get();
        }
        return resolvedDescription;
    }

    @Override
    public boolean isSatisfiedBy(T element) {
        try {
            return spec.isSatisfiedBy(element);
        } catch (RuntimeException e) {
            throw new GradleException("Could not evaluate spec for '" + getDisplayName() + "'.", e);
        }
    }

    @Override
    public String toString() {
        return "SelfDescribingSpec{description="
            + (resolvedDescription != null ? "'" + resolvedDescription + "'" : "<UNEVALUATED>")
            + '}';
    }
}
