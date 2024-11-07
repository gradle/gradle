/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Consumer;

public interface ConfigurationsProvider {
    /**
     * Returns the number of configurations in this provider.
     * <p>
     * This method is provided for performance reasons. It should be more efficient to call this method
     * than to call {@link #getAll()} and then call {@link Set#size()} on the result.
     *
     * @return the number of configurations in this provider, the same count as would be visited by calling {@link #getAll()}
     */
    int size();

    boolean isFixedSize();

    Set<? extends ConfigurationInternal> getAll();

    /**
     * Visit all consumable configurations provided by this configurations provider.
     * Lazy configurations which are known to be non-consumable are not realized.
     */
    void visitConsumable(Consumer<ConfigurationInternal> visitor);

    @Nullable
    ConfigurationInternal findByName(String name);

}
