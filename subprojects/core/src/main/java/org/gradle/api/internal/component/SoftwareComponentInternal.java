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

import org.gradle.api.Incubating;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The internal counterpart to {@link SoftwareComponent}.
 * <p>
 * Implementations of this interface must implement either {@link #getOutgoing()} or {@link #getUsages()}.
 */
public interface SoftwareComponentInternal extends SoftwareComponent {

    /**
     * This should be called {@code getVariants}, but that name is already taken by
     * {@link org.gradle.api.component.ComponentWithVariants}. Until that class is removed and
     * this method renamed, this API will remain incubating.
     * <p>
     * This method will be removed in favor of a public API.
     *
     * @return This component's variants.
     */
    @Incubating
    default Set<? extends SoftwareComponentVariant> getOutgoing() {
        return DeprecationLogger.whileDisabled(() ->
            getUsages().stream()
                .map(x -> x instanceof UsageContextShim ? ((UsageContextShim) x).variant : x)
                .collect(Collectors.toSet())
        );
    }

    /**
     * This method has been temporarily replaced by {@link #getOutgoing()} in order to change its return type.
     * Eventually, this functionality will be merged into {@link SoftwareComponent}.
     *
     * @deprecated Use {@link #getOutgoing()} This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    default Set<? extends UsageContext> getUsages() {
        // TODO Gradle 8.1: Add a deprecation nag here.
        return getOutgoing().stream().map(UsageContextShim::new).collect(Collectors.toSet());
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
