/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.Try;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Service to create providers from {@link ValueSource}s.
 *
 * Notifies interested parties when values are obtained from their sources.
 *
 * @since 6.1
 */
@ServiceScope(Scope.Build.class)
public interface ValueSourceProviderFactory {

    <T, P extends ValueSourceParameters> Provider<T> createProviderOf(
        Class<? extends ValueSource<T, P>> valueSourceType,
        Action<? super ValueSourceSpec<P>> configureAction
    );

    void addValueListener(ValueListener listener);

    void removeValueListener(ValueListener listener);

    void addComputationListener(ComputationListener listener);

    void removeComputationListener(ComputationListener listener);

    <T, P extends ValueSourceParameters> Provider<T> instantiateValueSourceProvider(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P parameters
    );

    /**
     * The listener that is notified when the value of the {@code ValueSource} is computed. There is no ordering guarantees with the
     * {@link ValueListener#valueObtained(ValueListener.ObtainedValue, ValueSource)}.
     */
    @EventScope(Scope.Build.class)
    interface ComputationListener {
        void beforeValueObtained();

        void afterValueObtained();
    }

    @EventScope(Scope.Build.class)
    interface ValueListener {
        <T, P extends ValueSourceParameters> void valueObtained(
            ObtainedValue<T, P> obtainedValue,
            ValueSource<T, P> source
        );

        interface ObtainedValue<T, P extends ValueSourceParameters> {

            Try<@org.jetbrains.annotations.Nullable T> getValue();

            Class<? extends ValueSource<T, P>> getValueSourceType();

            @Nullable
            Class<P> getValueSourceParametersType();

            @Nullable
            P getValueSourceParameters();
        }
    }
}
