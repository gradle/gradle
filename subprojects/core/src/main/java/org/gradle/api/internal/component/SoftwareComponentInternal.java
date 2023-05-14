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

import java.util.Set;

/**
 * This will be replaced by {@link org.gradle.api.component.ComponentWithVariants} and other public APIs.
 */
public interface SoftwareComponentInternal extends SoftwareComponent {
    /**
     * This should be called {@code getVariants} and return a set of {@link org.gradle.api.component.SoftwareComponentVariant}s,
     * though this is currently used by KMP and perhaps AGP due to there being a missing public API. However, renaming is
     * difficult since {@link org.gradle.api.component.ComponentWithVariants} already defines a {@code getVariants} method.
     * The existing {@code getVariants} method however is misnamed and should probably be called {@code getChildren}, and its
     * interface called {@code ComponentWithChildren}. The existing {@code ComponentWithVariants} actually defines an API for
     * declaring child components, which contribute their variants as defined by this method, as remote variants in GMM.
     * <p>
     * This method and {@link UsageContext} should both be deprecated in favor of a new public API.
     */
    Set<? extends UsageContext> getUsages();

    default void finalizeValue() {}
}
