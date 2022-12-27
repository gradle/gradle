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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * This will be replaced by {@link org.gradle.api.component.ComponentWithVariants} and other public APIs.
 * TODO: Or will it?
 */
public interface SoftwareComponentInternal extends SoftwareComponent {

    default SoftwareComponentPublications getOutgoing() {
        return new DefaultSoftwareComponentPublications(
            getUsages().stream()
                .map(x -> x instanceof UsageContextShim ? ((UsageContextShim) x).variant : x)
                .collect(Collectors.toSet())
        );
    }

    /**
     * Currently used by the kotlin multiplatform plugins.
     *
     * @deprecated Use {@link #getOutgoing()} ()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    default Set<? extends UsageContext> getUsages() {
        // TODO Gradle 8.1: Add a deprecation nag here.
        return getOutgoing().getVariants().stream().map(UsageContextShim::new).collect(Collectors.toSet());
    }

    /**
     * An implementation of {@link UsageContext} which delegates to a {@link SoftwareComponentVariant} instance.
     * This class should only be used to implement the deprecated {@link #getUsages()} method above, and
     * should be removed once the above method is removed.
     */
    @SuppressWarnings("deprecation")
    class UsageContextShim extends DelegatingSoftwareComponentVariant implements UsageContext {
        private final SoftwareComponentVariant variant;
        private UsageContextShim(SoftwareComponentVariant variant) {
            super(variant);
            this.variant = variant;
        }
    }
}
