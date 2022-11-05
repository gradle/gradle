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

/**
 * This will be replaced by {@link org.gradle.api.component.ComponentWithVariants} and other public APIs.
 */
public interface SoftwareComponentInternal extends SoftwareComponent {

    // TODO: Should we name this `getVariants`? `ComponentWithVariants` already defines a `getVariants` which
    // returns a different type. There are existing classes (in KGP) which implement both this class and
    // `ComponentWithVariants`.
    default Set<SoftwareComponentVariant> getAllVariants() {
        return Collections.unmodifiableSet(getUsages());
    }

    /**
     * Currently used by the kotlin plugins.
     *
     * @deprecated Use {@link #getAllVariants()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    default Set<? extends UsageContext> getUsages() {
        throw new UnsupportedOperationException("getUsages() is deprecated. Call getAllVariants() instead.");
    }
}
