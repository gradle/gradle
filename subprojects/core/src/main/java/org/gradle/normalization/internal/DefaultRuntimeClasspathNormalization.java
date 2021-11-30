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

package org.gradle.normalization.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.changedetection.state.IgnoringResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.IgnoringResourceFilter;
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.normalization.MetaInfNormalization;
import org.gradle.normalization.PropertiesFileNormalization;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultRuntimeClasspathNormalization implements RuntimeClasspathNormalizationInternal {
    private final MetaInfNormalization metaInfNormalization = new RuntimeMetaInfNormalization();
    private final EvaluatableFilter<ResourceFilter> resourceFilter = filter(IgnoringResourceFilter::new, ResourceFilter.FILTER_NOTHING);
    private final EvaluatableFilter<ResourceEntryFilter> manifestAttributeResourceFilter = filter(IgnoringResourceEntryFilter::new, ResourceEntryFilter.FILTER_NOTHING);
    private final DefaultPropertiesFileFilter propertyFileFilters = new DefaultPropertiesFileFilter();

    @Override
    public void ignore(String pattern) {
        resourceFilter.ignore(pattern);
    }

    @Override
    public void metaInf(Action<? super MetaInfNormalization> configuration) {
        configuration.execute(metaInfNormalization);
    }

    @Override
    public void properties(String pattern, Action<? super PropertiesFileNormalization> configuration) {
        propertyFileFilters.configure(pattern, configuration);
    }

    @Override
    public void properties(Action<? super PropertiesFileNormalization> configuration) {
        properties(PropertiesFileFilter.ALL_PROPERTIES, configuration);
    }

    @Override
    public ResourceFilter getClasspathResourceFilter() {
        return resourceFilter.evaluate();
    }

    @Override
    public ResourceEntryFilter getManifestAttributeResourceEntryFilter() {
        return manifestAttributeResourceFilter.evaluate();
    }

    @Override
    public Map<String, ResourceEntryFilter> getPropertiesFileFilters() {
        return propertyFileFilters.getFilters();
    }

    public class RuntimeMetaInfNormalization implements MetaInfNormalization {
        @Override
        public void ignoreCompletely() {
            ignore("META-INF/*");
        }

        @Override
        public void ignoreManifest() {
            ignore("META-INF/MANIFEST.MF");
        }

        @Override
        public void ignoreAttribute(String name) {
            manifestAttributeResourceFilter.ignore(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public void ignoreProperty(String name) {
            propertyFileFilters.configure("META-INF/**/*.properties", propertiesFileNormalization -> propertiesFileNormalization.ignoreProperty(name));
        }
    }

    @Override
    public CachedState computeCachedState() {
        DefaultCachedState cachedState = new DefaultCachedState(resourceFilter, manifestAttributeResourceFilter, propertyFileFilters);
        if (cachedState.isTrivial()) {
            return null;
        }
        return cachedState;
    }

    @Override
    public void configureFromCachedState(CachedState state) {
        if (!(state instanceof DefaultCachedState)) {
            throw new IllegalArgumentException("Cannot restore state from " + state.getClass() + ", expecting DefaultCachedState");
        }
        DefaultCachedState defaultCachedState = (DefaultCachedState) state;
        defaultCachedState.resourceFilterState.forEach(resourceFilter::ignore);
        defaultCachedState.manifestAttributesFilterState.forEach(manifestAttributeResourceFilter::ignore);
        defaultCachedState.propertiesFileFiltersState.forEach((pattern, ignores) -> {
            propertyFileFilters.configure(pattern, normalization -> ignores.forEach(normalization::ignoreProperty));
        });
    }

    private static <T> EvaluatableFilter<T> filter(Function<ImmutableSet<String>, T> initializer, T emptyValue) {
        return new EvaluatableFilter<>(initializer, emptyValue);
    }

    private static class EvaluatableFilter<T> {
        private T value;
        private final Supplier<T> valueSupplier;
        private final ImmutableSet.Builder<String> builder;

        public EvaluatableFilter(Function<ImmutableSet<String>, T> initializer, T emptyValue) {
            this.builder = ImmutableSet.builder();
            // if there are configured ignores, use the initializer to create the value, otherwise return emptyValue
            this.valueSupplier = () -> Optional.of(builder.build())
                                                .filter(ignores -> !ignores.isEmpty())
                                                .map(initializer)
                                                .orElse(emptyValue);
        }

        public T evaluate() {
            if (value == null) {
                value = valueSupplier.get();
            }
            return value;
        }

        private void checkNotEvaluated() {
            if (value != null) {
                throw new GradleException("Cannot configure runtime classpath normalization after execution started.");
            }
        }

        public void ignore(String ignore) {
            checkNotEvaluated();
            builder.add(ignore);
        }

        Set<String> getState() {
            // Ensure that configuration is finished to avoid skipping anything from cache.
            evaluate();
            return builder.build();
        }
    }

    private static class DefaultPropertiesFileFilter implements PropertiesFileFilter {
        private final Map<String, EvaluatableFilter<ResourceEntryFilter>> propertyFilters = Maps.newHashMap();
        private Map<String, ResourceEntryFilter> finalPropertyFilters;

        DefaultPropertiesFileFilter() {
            propertyFilters.put(PropertiesFileFilter.ALL_PROPERTIES, filter(IgnoringResourceEntryFilter::new, ResourceEntryFilter.FILTER_NOTHING));
        }

        @Override
        public Map<String, ResourceEntryFilter> getFilters() {
            if (finalPropertyFilters == null) {
                ImmutableMap.Builder<String, ResourceEntryFilter> builder = ImmutableMap.builder();
                propertyFilters.forEach((pattern, filter) -> builder.put(pattern, filter.evaluate()));
                finalPropertyFilters = builder.build();
            }
            return finalPropertyFilters;
        }

        void configure(String pattern, Action<? super PropertiesFileNormalization> configuration) {
            EvaluatableFilter<ResourceEntryFilter> filter;
            if (finalPropertyFilters == null) {
                if (propertyFilters.containsKey(pattern)) {
                    filter = propertyFilters.get(pattern);
                } else {
                    filter = filter(IgnoringResourceEntryFilter::new, ResourceEntryFilter.FILTER_NOTHING);
                    propertyFilters.put(pattern, filter);
                }
                PropertiesFileNormalization normalization = filter::ignore;
                configuration.execute(normalization);
            } else {
                throw new IllegalStateException("Cannot configure runtime classpath normalization after execution started.");
            }
        }

        Map<String, Set<String>> getState() {
            // Ensure that configuration is finished to avoid skipping anything from cache.
            getFilters();
            return propertyFilters.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> e.getValue().getState()));
        }
    }

    private static class DefaultCachedState implements CachedState {
        final Set<String> resourceFilterState;
        final Set<String> manifestAttributesFilterState;
        final Map<String, Set<String>> propertiesFileFiltersState;

        DefaultCachedState(EvaluatableFilter<?> resourceFilter, EvaluatableFilter<?> manifestAttributesFilter, DefaultPropertiesFileFilter propertiesFileFilters) {
            resourceFilterState = resourceFilter.getState();
            manifestAttributesFilterState = manifestAttributesFilter.getState();
            propertiesFileFiltersState = propertiesFileFilters.getState();
        }

        boolean isTrivial() {
            return resourceFilterState.isEmpty() && manifestAttributesFilterState.isEmpty() && propertiesFileFiltersState.values().stream().allMatch(Set::isEmpty);
        }
    }
}
