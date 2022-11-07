/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.component;

import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This will be replaced by {@link org.gradle.api.component.ComponentWithVariants} and other public APIs.
 * TODO: Or will it?
 */
public interface SoftwareComponentInternal extends SoftwareComponent {

    // TODO: Should we name this `getVariants`? `ComponentWithVariants` already defines a `getVariants` which
    // returns a different type. There are existing classes (internally and in KGP) which implement both this class and
    // `ComponentWithVariants`.
    default Set<SoftwareComponentVariant> getAllVariants() {
        // TODO Gradle 8.1: Add a deprecation nag here. Subclasses should implement this method
        // instead of getUsages. In 9.0, we'll remove the default implementation.
        return Collections.unmodifiableSet(getUsages());
    }

    /**
     * Currently used by the kotlin plugins.
     *
     * @deprecated Use {@link #getAllVariants()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    default Set<? extends UsageContext> getUsages() {
        // TODO Gradle 8.1: Add a deprecation nag here.
        return getAllVariants().stream().map(UsageContextShim::new).collect(Collectors.toSet());
    }

    /**
     * An implementation of {@link UsageContext} which delegates to a {@link SoftwareComponentVariant} instance.
     * This class should only be used to implement the deprecated {@link #getUsages()} method above, and
     * should be removed once the above method is removed.
     */
    @SuppressWarnings("deprecation")
    class UsageContextShim extends DefaultSoftwareComponentVariant implements UsageContext {
        private UsageContextShim(SoftwareComponentVariant variant) {
            super(
                variant.getName(),
                variant.getAttributes(),
                variant.getArtifacts(),
                variant.getDependencies(),
                variant.getDependencyConstraints(),
                variant.getCapabilities(),
                variant.getGlobalExcludes()
            );
        }
    }
}
