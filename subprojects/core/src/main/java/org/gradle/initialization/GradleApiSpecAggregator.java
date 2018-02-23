/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.initialization.GradleApiSpecProvider.Spec;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.service.DefaultServiceLocator;

import java.util.List;
import java.util.Set;

class GradleApiSpecAggregator {

    private final ClassLoader classLoader;

    public GradleApiSpecAggregator(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Spec aggregate() {
        List<Class<? extends GradleApiSpecProvider>> providers = providers();
        if (providers.isEmpty()) {
            return emptySpec();
        }

        if (providers.size() == 1) {
            return specFrom(providers.get(0));
        }

        return mergeSpecsOf(providers);
    }

    private GradleApiSpecProvider.SpecAdapter emptySpec() {
        return new GradleApiSpecProvider.SpecAdapter();
    }

    private Spec specFrom(Class<? extends GradleApiSpecProvider> provider) {
        return instantiate(provider).get();
    }

    private Spec mergeSpecsOf(List<Class<? extends GradleApiSpecProvider>> providers) {
        final ImmutableSet.Builder<String> exportedPackages = ImmutableSet.builder();
        final ImmutableSet.Builder<String> exportedResourcePrefixes = ImmutableSet.builder();
        for (Class<? extends GradleApiSpecProvider> provider : providers) {
            Spec spec = specFrom(provider);
            exportedPackages.addAll(spec.getExportedPackages());
            exportedResourcePrefixes.addAll(spec.getExportedResourcePrefixes());
        }
        return new DefaultSpec(exportedPackages.build(), exportedResourcePrefixes.build());
    }

    private List<Class<? extends GradleApiSpecProvider>> providers() {
        return new DefaultServiceLocator(classLoader).implementationsOf(GradleApiSpecProvider.class);
    }

    private GradleApiSpecProvider instantiate(Class<? extends GradleApiSpecProvider> providerClass) {
        return DirectInstantiator.INSTANCE.newInstance(providerClass);
    }

    static class DefaultSpec implements Spec {

        private final ImmutableSet<String> exportedPackages;
        private final ImmutableSet<String> exportedResourcePrefixes;

        DefaultSpec(ImmutableSet<String> exportedPackages, ImmutableSet<String> exportedResourcePrefixes) {
            this.exportedPackages = exportedPackages;
            this.exportedResourcePrefixes = exportedResourcePrefixes;
        }

        @Override
        public Set<String> getExportedPackages() {
            return exportedPackages;
        }

        @Override
        public Set<String> getExportedResourcePrefixes() {
            return exportedResourcePrefixes;
        }
    }
}
