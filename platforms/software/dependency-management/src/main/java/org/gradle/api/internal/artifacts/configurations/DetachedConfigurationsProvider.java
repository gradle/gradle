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

import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.function.Consumer;

public class DetachedConfigurationsProvider implements ConfigurationsProvider {
    private ConfigurationInternal theOnlyConfiguration;

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isFixedSize() {
        return true;
    }

    @Override
    public Set<ConfigurationInternal> getAll() {
        return ImmutableSet.of(theOnlyConfiguration);
    }

    @Override
    public void visitConsumable(Consumer<ConfigurationInternal> visitor) {
        // In Gradle 9.0, detached configurations will never be consumable.
        // Until then, visit it.
        if (theOnlyConfiguration.isCanBeConsumed()) {
            visitor.accept(theOnlyConfiguration);
        }
    }

    @Override
    public ConfigurationInternal findByName(String name) {
        if (name.equals(theOnlyConfiguration.getName())) {
            return theOnlyConfiguration;
        }
        return null;
    }

    public void setTheOnlyConfiguration(ConfigurationInternal theOnlyConfiguration) {
        this.theOnlyConfiguration = theOnlyConfiguration;
    }
}
