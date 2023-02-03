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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.util.internal.ConfigureUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class ConfigureByMapAction<T> implements Action<T> {

    private final Map<?, ?> properties;
    private final Collection<?> mandatoryProperties;

    public ConfigureByMapAction(Map<?, ?> properties) {
        this(properties, Collections.emptySet());
    }

    public ConfigureByMapAction(Map<?, ?> properties, Collection<?> mandatoryProperties) {
        this.properties = properties;
        this.mandatoryProperties = mandatoryProperties;
    }

    @Override
    public void execute(T thing) {
        ConfigureUtil.configureByMap(properties, thing, mandatoryProperties);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConfigureByMapAction that = (ConfigureByMapAction) o;

        if (!mandatoryProperties.equals(that.mandatoryProperties)) {
            return false;
        }
        if (!properties.equals(that.properties)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = properties.hashCode();
        result = 31 * result + mandatoryProperties.hashCode();
        return result;
    }
}
