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
 * Extension point for Gradle modules to extend the set of available packages and
 * resources which should be visible from the Gradle API ClassLoader.
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
        Set<String> getAllowedPackages();

        /**
         * Set of resource prefixes which should be visible from the Gradle API ClassLoader.
         */
        Set<String> getAllowedResources();
    }

    class SpecBuilder {

        private final Set<String> allowedPackages = new LinkedHashSet<String>();
        private final Set<String> allowedResources = new LinkedHashSet<String>();

        /**
         * Marks a package and all its sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageName the package name
         */
        public SpecBuilder allowPackage(String packageName) {
            allowedPackages.add(packageName);
            return this;
        }

        /**
         * Marks a set of packages and all their sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageNames the set of package names
         */
        public SpecBuilder allowPackages(String... packageNames) {
            allowedPackages.addAll(Arrays.asList(packageNames));
            return this;
        }

        /**
         * Marks all resources with the given prefix as visible.
         *
         * @param resourcePrefix the resource prefix
         */
        public SpecBuilder allowResources(String resourcePrefix) {
            allowedResources.add(resourcePrefix);
            return this;
        }

        /**
         * Merges a Spec into the current set of allowed packages and resources.
         *
         * @param spec the spec to be merged
         */
        public SpecBuilder merge(Spec spec) {
            allowedPackages.addAll(spec.getAllowedPackages());
            allowedResources.addAll(spec.getAllowedResources());
            return this;
        }

        /**
         * Builds the final (immutable) Spec with all allowed packages and resources.
         *
         * @return the Spec
         */
        public Spec build() {
            return new DefaultSpec(ImmutableSet.copyOf(allowedPackages), ImmutableSet.copyOf(allowedResources));
        }

        static class DefaultSpec implements Spec {

            private final ImmutableSet<String> allowedPackages;
            private final ImmutableSet<String> allowedResources;

            DefaultSpec(ImmutableSet<String> allowedPackages, ImmutableSet<String> allowedResources) {
                this.allowedResources = allowedResources;
                this.allowedPackages = allowedPackages;
            }

            @Override
            public Set<String> getAllowedPackages() {
                return allowedPackages;
            }

            @Override
            public Set<String> getAllowedResources() {
                return allowedResources;
            }
        }
    }
}
