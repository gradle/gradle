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
import org.gradle.api.Incubating;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extension point for Gradle modules to extend the set of packages and
 * resource prefixes exported by the Gradle API ClassLoader.
 *
 * A SPI suitable for use with Java's {@link java.util.ServiceLoader}.
 */
@Incubating
public interface GradleApiSpecProvider {

    Spec get();

    interface Spec {
        /**
         * Set of packages and enclosing sub-packages which should be visible from the Gradle API ClassLoader.
         *
         * Resources in those packages will also be visible.
         */
        Set<String> getExportedPackages();

        /**
         * Set of resource prefixes which should be visible from the Gradle API ClassLoader.
         */
        Set<String> getExportedResourcePrefixes();
    }

    class SpecBuilder {

        private final Set<String> exportedPackages = new LinkedHashSet<String>();
        private final Set<String> exportedResourcePrefixes = new LinkedHashSet<String>();

        /**
         * Marks a package and all its sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageName the package name
         */
        public SpecBuilder exportPackage(String packageName) {
            exportedPackages.add(packageName);
            return this;
        }

        /**
         * Marks a set of packages and all their sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageNames the set of package names
         */
        public SpecBuilder exportPackages(String... packageNames) {
            exportedPackages.addAll(Arrays.asList(packageNames));
            return this;
        }

        /**
         * Marks all resources with the given prefix as visible.
         *
         * @param resourcePrefix the resource prefix
         */
        public SpecBuilder exportResourcePrefix(String resourcePrefix) {
            exportedResourcePrefixes.add(resourcePrefix);
            return this;
        }

        /**
         * Merges a Spec into the current set of allowed packages and resources.
         *
         * @param spec the spec to be merged
         */
        public SpecBuilder merge(Spec spec) {
            exportedPackages.addAll(spec.getExportedPackages());
            exportedResourcePrefixes.addAll(spec.getExportedResourcePrefixes());
            return this;
        }

        /**
         * Builds the final (immutable) Spec with all allowed packages and resources.
         *
         * @return the Spec
         */
        public Spec build() {
            return new DefaultSpec(ImmutableSet.copyOf(exportedPackages), ImmutableSet.copyOf(exportedResourcePrefixes));
        }

        static class DefaultSpec implements Spec {

            private final ImmutableSet<String> exportedPackages;
            private final ImmutableSet<String> exportedResourcePrefixes;

            DefaultSpec(ImmutableSet<String> exportedPackages, ImmutableSet<String> exportedResourcePrefixes) {
                this.exportedResourcePrefixes = exportedResourcePrefixes;
                this.exportedPackages = exportedPackages;
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
}
