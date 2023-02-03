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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceLocator;

import java.util.List;
import java.util.Set;

class GradleApiSpecAggregator {

    private final ClassLoader classLoader;
    private final Instantiator instantiator;

    public GradleApiSpecAggregator(ClassLoader classLoader, Instantiator instantiator) {
        this.classLoader = classLoader;
        this.instantiator = instantiator;
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
        final ImmutableSet.Builder<Class<?>> exportedClasses = ImmutableSet.builder();
        final ImmutableSet.Builder<String> exportedPackages = ImmutableSet.builder();
        final ImmutableSet.Builder<String> exportedResources = ImmutableSet.builder();
        final ImmutableSet.Builder<String> exportedResourcePrefixes = ImmutableSet.builder();
        for (Class<? extends GradleApiSpecProvider> provider : providers) {
            Spec spec = specFrom(provider);
            exportedClasses.addAll(spec.getExportedClasses());
            exportedPackages.addAll(spec.getExportedPackages());
            exportedResources.addAll(spec.getExportedResources());
            exportedResourcePrefixes.addAll(spec.getExportedResourcePrefixes());
        }
        return new DefaultSpec(exportedClasses.build(), exportedPackages.build(), exportedResources.build(), exportedResourcePrefixes.build());
    }

    private List<Class<? extends GradleApiSpecProvider>> providers() {
        return new DefaultServiceLocator(classLoader).implementationsOf(GradleApiSpecProvider.class);
    }

    private GradleApiSpecProvider instantiate(Class<? extends GradleApiSpecProvider> providerClass) {
        return instantiator.newInstance(providerClass);
    }

    static class DefaultSpec implements Spec {

        private final ImmutableSet<Class<?>> exportedClasses;
        private final ImmutableSet<String> exportedPackages;
        private final ImmutableSet<String> exportedResources;
        private final ImmutableSet<String> exportedResourcePrefixes;

        DefaultSpec(ImmutableSet<Class<?>> exportedClasses, ImmutableSet<String> exportedPackages, ImmutableSet<String> exportedResources, ImmutableSet<String> exportedResourcePrefixes) {
            this.exportedClasses = exportedClasses;
            this.exportedPackages = exportedPackages;
            this.exportedResources = exportedResources;
            this.exportedResourcePrefixes = exportedResourcePrefixes;
        }

        @Override
        public Set<Class<?>> getExportedClasses() {
            return exportedClasses;
        }

        @Override
        public Set<String> getExportedPackages() {
            return exportedPackages;
        }

        @Override
        public Set<String> getExportedResources() {
            return exportedResources;
        }

        @Override
        public Set<String> getExportedResourcePrefixes() {
            return exportedResourcePrefixes;
        }
    }
}
