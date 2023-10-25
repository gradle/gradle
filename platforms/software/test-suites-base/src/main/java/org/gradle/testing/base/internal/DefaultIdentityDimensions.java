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

package org.gradle.testing.base.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.testing.base.Identity;
import org.gradle.testing.base.IdentityDimensions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class DefaultIdentityDimensions implements IdentityDimensions {
    private final MapProperty<String, SetProperty<Object>> dimensions = Cast.uncheckedNonnullCast(
        getObjectFactory().mapProperty(String.class, SetProperty.class)
    );
    private final SetProperty<Spec<? super Identity>> includes = Cast.uncheckedNonnullCast(
        getObjectFactory().setProperty(Spec.class)
    );
    private final SetProperty<Spec<? super Identity>> excludes = Cast.uncheckedNonnullCast(
        getObjectFactory().setProperty(Spec.class)
    );
    private final Provider<Set<Identity>> identities = getProviderFactory().of(
        IdentitiesValueSource.class,
        spec -> spec.parameters(p -> {
            p.getDimensions().set(dimensions.map(dimMap -> {
                ImmutableMap.Builder<String, Set<Object>> result = ImmutableMap.builderWithExpectedSize(dimMap.size());
                dimMap.forEach((k, v) -> result.put(k, v.get()));
                return result.build();
            }));
            p.getIncludes().set(includes);
            p.getExcludes().set(excludes);
        })
    );

    public static abstract class IdentitiesValueSource implements ValueSource<Set<Identity>, IdentitiesValueSourceParameters> {
        private static final class DimEntry {
            String dimension;
            Object value;

            DimEntry(String dimension, Object value) {
                this.dimension = dimension;
                this.value = value;
            }
        }

        @Nullable
        @Override
        public Set<Identity> obtain() {
            Set<List<DimEntry>> product = Sets.cartesianProduct(
                getParameters().getDimensions().get().entrySet().stream()
                    // Map each dimension set into a list of entries (keeping iteration order)
                    .map(e -> e.getValue().stream().map(v -> new DimEntry(e.getKey(), v)).collect(ImmutableSet.toImmutableSet()))
                    // Collect each dimension (List<DimEntry>) into a list
                    .collect(ImmutableList.toImmutableList())
            );

            // TODO optimize by keeping the product list sometimes instead of doing a copy
            // only needed if the product is too large to fit in a reasonable amount of memory
            Set<Spec<? super Identity>> includes = getParameters().getIncludes().get();
            Set<Spec<? super Identity>> excludes = getParameters().getExcludes().get();
            return product.stream()
                .map(entries -> new DefaultIdentity(
                    entries.stream()
                        .collect(ImmutableMap.toImmutableMap(e -> e.dimension, e -> e.value))
                ))
                .filter(target -> includes.stream().allMatch(s -> s.isSatisfiedBy(target)))
                .filter(target -> excludes.stream().noneMatch(s -> s.isSatisfiedBy(target)))
                .collect(ImmutableSet.toImmutableSet());
        }
    }

    public interface IdentitiesValueSourceParameters extends ValueSourceParameters {
        Property<Map<String, Set<Object>>> getDimensions();
        SetProperty<Spec<? super Identity>> getIncludes();
        SetProperty<Spec<? super Identity>> getExcludes();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Override
    public void include(Spec<? super Identity> spec) {
        includes.add(spec);
    }

    @Override
    public void exclude(Spec<? super Identity> spec) {
        excludes.add(spec);
    }

    @Override
    public <T> SetProperty<T> dimension(String dimension) {
        Provider<SetProperty<Object>> provider = dimensions.getting(dimension);
        if (!provider.isPresent()) {
            SetProperty<Object> value = getObjectFactory().setProperty(Object.class);
            dimensions.put(dimension, value);
            return Cast.uncheckedCast(value);
        }
        return Cast.uncheckedCast(provider.get());
    }

    @Override
    public Provider<Set<Identity>> getIdentities() {
        return identities;
    }
}
