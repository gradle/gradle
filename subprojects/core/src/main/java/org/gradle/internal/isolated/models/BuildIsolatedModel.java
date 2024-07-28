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

package org.gradle.internal.isolated.models;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.isolated.models.IsolatedProviderFactory.IsolatedProviderForGradle;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public class BuildIsolatedModel<T> {

    private final IsolatedProviderFactory isolatedProviderFactory;

    private final GradleInternal owner;

    @Nullable
    private Provider<T> producer;
    @Nullable
    private IsolatedProviderForGradle<T> isolatedProducer;

    public BuildIsolatedModel(
        IsolatedProviderFactory isolatedProviderFactory,
        Provider<T> producer,
        GradleInternal owner
    ) {
        this.isolatedProviderFactory = isolatedProviderFactory;
        this.producer = producer;
        this.owner = owner;
    }

    public void isolateIfNotAlready() {
        synchronized (this) {
            if (isolatedProducer != null) {
                return;
            }

            isolatedProducer = isolatedProviderFactory.isolate(requireNonNull(producer), owner);
            producer = null;
        }
    }

    public Provider<T> instantiate() {
        synchronized (this) {
            isolateIfNotAlready();
            return requireNonNull(isolatedProducer).instantiate(owner);
        }
    }

}
