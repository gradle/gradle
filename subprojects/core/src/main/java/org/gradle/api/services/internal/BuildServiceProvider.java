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

package org.gradle.api.services.internal;

import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.state.Managed;

/**
 * A provider for build services that are registered or consumed.
 */
@SuppressWarnings("rawtypes")
public abstract class BuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends AbstractMinimalProvider<T> implements Managed {

    public interface Listener {
        Listener EMPTY = provider -> {
        };

        void beforeGet(BuildServiceProvider<?, ?> provider);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return true;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public Object unpackState() {
        throw new UnsupportedOperationException("Build services cannot be serialized.");
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return ExecutionTimeValue.changingValue(this);
    }

    public void maybeStop() {
        // subclasses to override
    }

    @SuppressWarnings("unused") // Used via instrumentation
    public static <P extends BuildServiceParameters, T extends BuildService<P>> void setBuildServiceAsConvention(DefaultProperty<T> property, ServiceRegistry serviceRegistry, String buildServiceName) {
        ConsumedBuildServiceProvider<T, P> lazyProvider = new ConsumedBuildServiceProvider<>(buildServiceName, property.getType(), serviceRegistry);
        property.convention(lazyProvider);
    }

    public abstract BuildServiceDetails<T, P> getServiceDetails();

    public abstract String getName();

    /**
     * Are the given providers referring to the same service provider?
     *
     * This method does not distinguish between consumed/registered providers.
     */
    public static boolean isSameService(Provider<? extends BuildService<?>> thisProvider, Provider<? extends BuildService<?>> anotherProvider) {
        if (!(thisProvider instanceof BuildServiceProvider && anotherProvider instanceof BuildServiceProvider)) {
            return false;
        }
        if (thisProvider == anotherProvider) {
            return true;
        }
        BuildServiceProvider thisBuildServiceProvider = (BuildServiceProvider) thisProvider;
        BuildServiceProvider otherBuildServiceProvider = (BuildServiceProvider) anotherProvider;
        return thisBuildServiceProvider.getName().equals(otherBuildServiceProvider.getName()) && thisBuildServiceProvider.getType() == otherBuildServiceProvider.getType();
    }
}
