/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.configuration.internal;

import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;

public class DefaultBuildCacheServiceRegistration implements BuildCacheServiceRegistration {
    private final Class<? extends BuildCacheServiceFactory<?>> factoryType;
    private final Class<? extends BuildCache> configurationType;

    public DefaultBuildCacheServiceRegistration(Class<? extends BuildCache> configurationType, Class<? extends BuildCacheServiceFactory<?>> factoryType) {
        this.factoryType = factoryType;
        this.configurationType = configurationType;
    }

    @Override
    public Class<? extends BuildCache> getConfigurationType() {
        return configurationType;
    }

    @Override
    public Class<? extends BuildCacheServiceFactory<?>> getFactoryType() {
        return factoryType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultBuildCacheServiceRegistration that = (DefaultBuildCacheServiceRegistration) o;

        if (!factoryType.equals(that.factoryType)) {
            return false;
        }
        return configurationType.equals(that.configurationType);
    }

    @Override
    public int hashCode() {
        int result = factoryType.hashCode();
        result = 31 * result + configurationType.hashCode();
        return result;
    }
}
