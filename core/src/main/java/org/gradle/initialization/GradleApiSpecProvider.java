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

import java.util.Set;

/**
 * Extension point for Gradle modules to extend the set of packages and
 * resource prefixes exported by the Gradle API ClassLoader.
 *
 * A SPI suitable for use with Java's {@link java.util.ServiceLoader}.
 */
public interface GradleApiSpecProvider {

    Spec get();

    interface Spec {
        /**
         * Set of classes which should be visible from the Gradle API ClassLoader.
         */
        Set<Class<?>> getExportedClasses();

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

        /**
         * Set of resources which should be visible from the Gradle API ClassLoader.
         */
        Set<String> getExportedResources();
    }

    /**
     * Empty {@link Spec} implementation to be extended by SPI implementers to isolate them
     * from changes to the interface.
     */
    class SpecAdapter implements Spec {

        @Override
        public Set<Class<?>> getExportedClasses() {
            return ImmutableSet.of();
        }

        @Override
        public Set<String> getExportedPackages() {
            return ImmutableSet.of();
        }

        @Override
        public Set<String> getExportedResourcePrefixes() {
            return ImmutableSet.of();
        }

        @Override
        public Set<String> getExportedResources() {
            return ImmutableSet.of();
        }
    }
}
