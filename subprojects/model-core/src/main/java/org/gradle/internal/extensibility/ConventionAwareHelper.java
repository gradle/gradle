/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.internal.Cast;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.internal.GUtil.uncheckedCall;

@SuppressWarnings("deprecation")
public class ConventionAwareHelper implements ConventionMapping, org.gradle.api.internal.HasConvention {
    //prefix internal fields with _ so that they don't get into the way of propertyMissing()
    private final org.gradle.api.plugins.Convention _convention;
    private final IConventionAware _source;
    // These are properties that could have convention mapping applied to them
    private final Set<String> _propertyNames;
    // These are properties that should not be allowed to use convention mapping
    private final Set<String> _ineligiblePropertyNames;

    private final Map<String, MappedPropertyImpl> _mappings = new HashMap<String, MappedPropertyImpl>();

    public ConventionAwareHelper(IConventionAware source, org.gradle.api.plugins.Convention convention) {
        this._source = source;
        this._convention = convention;
        this._propertyNames = JavaPropertyReflectionUtil.propertyNames(source);
        this._ineligiblePropertyNames = new HashSet<>();
    }

    private MappedProperty map(String propertyName, MappedPropertyImpl mapping) {
        if (!_propertyNames.contains(propertyName)) {
            throw new InvalidUserDataException(
                "You can't map a property that does not exist: propertyName=" + propertyName);
        }

        if (_ineligiblePropertyNames.contains(propertyName)) {
            throw DocumentedFailure.builder()
                .withSummary("Using internal convention mapping with a Provider backed property.")
                .withUpgradeGuideSection(7, "convention_mapping")
                .build();
        }

        _mappings.put(propertyName, mapping);
        return mapping;
    }

    @Override
    public MappedProperty map(String propertyName, final Closure<?> value) {
        return map(propertyName, new MappedPropertyImpl() {
            @Override
            public Object doGetValue(org.gradle.api.plugins.Convention convention, IConventionAware conventionAwareObject) {
                switch (value.getMaximumNumberOfParameters()) {
                    case 0:
                        return value.call();
                    case 1:
                        return value.call(convention);
                    default:
                        return value.call(convention, conventionAwareObject);
                }
            }
        });
    }

    @Override
    public MappedProperty map(String propertyName, final Callable<?> value) {
        return map(propertyName, new MappedPropertyImpl() {
            @Override
            public Object doGetValue(org.gradle.api.plugins.Convention convention, IConventionAware conventionAwareObject) {
                return uncheckedCall(value);
            }
        });
    }

    public void propertyMissing(String name, Object value) {
        if (value instanceof Closure) {
            map(name, Cast.<Closure<?>>uncheckedNonnullCast(value));
        } else {
            throw new MissingPropertyException(name, getClass());
        }
    }

    @Override
    public void ineligible(String propertyName) {
        _ineligiblePropertyNames.add(propertyName);
    }

    @Override
    public <T> T getConventionValue(T actualValue, String propertyName, boolean isExplicitValue) {
        if (isExplicitValue) {
            return actualValue;
        }

        T returnValue = actualValue;
        if (_mappings.containsKey(propertyName)) {
            boolean useMapping = true;
            if (actualValue instanceof Collection && !((Collection<?>) actualValue).isEmpty()) {
                useMapping = false;
            } else if (actualValue instanceof Map && !((Map<?, ?>) actualValue).isEmpty()) {
                useMapping = false;
            }
            if (useMapping) {
                returnValue = Cast.uncheckedNonnullCast(_mappings.get(propertyName).getValue(_convention, _source));
            }
        }
        return returnValue;
    }

    @Deprecated
    @Override
    public org.gradle.api.plugins.Convention getConvention() {
        DeprecationLogger.deprecateType(org.gradle.api.internal.HasConvention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_access_to_conventions")
            .nagUser();
        return _convention;
    }

    private static abstract class MappedPropertyImpl implements MappedProperty {
        private boolean haveValue;
        private boolean cache;
        private Object cachedValue;

        public Object getValue(org.gradle.api.plugins.Convention convention, IConventionAware conventionAwareObject) {
            if (!cache) {
                return doGetValue(convention, conventionAwareObject);
            }
            if (!haveValue) {
                cachedValue = doGetValue(convention, conventionAwareObject);
                haveValue = true;
            }
            return cachedValue;
        }

        @Override
        public void cache() {
            cache = true;
            cachedValue = null;
        }

        abstract Object doGetValue(org.gradle.api.plugins.Convention convention, IConventionAware conventionAwareObject);
    }
}
