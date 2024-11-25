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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.state.Managed;

import javax.annotation.Nonnull;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * A provider for build services that are registered or consumed.
 */
@SuppressWarnings("rawtypes")
public abstract class BuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends AbstractMinimalProvider<T> implements Managed {

    static <T> Class<T> getProvidedType(Provider<T> provider) {
        return ((ProviderInternal<T>) provider).getType();
    }

    static BuildServiceProvider<?, ?> asBuildServiceProvider(Provider<? extends BuildService<?>> service) {
        if (service instanceof BuildServiceProvider) {
            return uncheckedCast(service);
        }
        throw new UnsupportedOperationException("Unexpected provider for a build service: " + service);
    }

    public boolean isConstrained() {
        return getServiceDetails().isConstrained();
    }

    public interface Listener {
        Listener EMPTY = provider -> {
        };

        void beforeGet(BuildServiceProvider<?, ?> provider);
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
    public static <P extends BuildServiceParameters, T extends BuildService<P>> void setBuildServiceAsConvention(@Nonnull DefaultProperty<T> property, ServiceLookup serviceLookup, String buildServiceName) {
        BuildServiceRegistryInternal buildServiceRegistry = (BuildServiceRegistryInternal) serviceLookup.get(BuildServiceRegistry.class);
        BuildServiceProvider<T, P> consumer = Cast.uncheckedCast(buildServiceRegistry.consume(buildServiceName, property.getType()));
        property.convention(consumer);
    }

    public abstract BuildServiceDetails<T, P> getServiceDetails();

    public abstract String getName();

    @Override
    @Nonnull
    public abstract Class<T> getType();

    /**
     * Returns the identifier for the build that owns this service.
     */
    public abstract BuildIdentifier getBuildIdentifier();

    /**
     * Are the given providers referring to the same service provider?
     *
     * This method does not distinguish between consumed/registered providers.
     */
    public static boolean isSameService(Provider<? extends BuildService<?>> thisProvider, Provider<? extends BuildService<?>> anotherProvider) {
        if (thisProvider == anotherProvider) {
            return true;
        }
        if (!(thisProvider instanceof BuildServiceProvider && anotherProvider instanceof BuildServiceProvider)) {
            return false;
        }
        BuildServiceProvider thisBuildServiceProvider = (BuildServiceProvider) thisProvider;
        BuildServiceProvider otherBuildServiceProvider = (BuildServiceProvider) anotherProvider;
        String thisName = thisBuildServiceProvider.getName();
        String otherName = otherBuildServiceProvider.getName();
        if (!thisName.isEmpty() && !otherName.isEmpty() && !thisName.equals(otherName)) {
            return false;
        }
        if (!isCompatibleServiceType(thisBuildServiceProvider, otherBuildServiceProvider)) {
            return false;
        }
        return thisBuildServiceProvider.getBuildIdentifier().equals(otherBuildServiceProvider.getBuildIdentifier());
    }

    private static boolean isCompatibleServiceType(BuildServiceProvider thisBuildServiceProvider, BuildServiceProvider otherBuildServiceProvider) {
        Class<?> otherType = otherBuildServiceProvider.getType();
        Class<?> thisType = thisBuildServiceProvider.getType();
        return otherType.isAssignableFrom(Cast.uncheckedCast(thisType));
    }
}
