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
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.Convention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.reflect.ClassInspector;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.util.GUtil.uncheckedCall;

public class ConventionAwareHelper implements ConventionMapping, HasConvention {
    //prefix internal fields with _ so that they don't get into the way of propertyMissing()
    private final Convention _convention;
    private final IConventionAware _source;
    private final Map<String, Boolean> _propertyNames;
    private final Map<String, MappedPropertyImpl> _mappings = new HashMap<String, MappedPropertyImpl>();

    public ConventionAwareHelper(IConventionAware source, Convention convention) {
        this._source = source;
        this._convention = convention;
        this._propertyNames = ClassInspector.inspect(source.getClass()).getProperties().stream().collect(Collectors.toMap(it -> it.getName(), it -> isProviderType(it.getGetters())));
    }

    private static boolean isProviderType(List<Method> getters) {
        return getters.stream().anyMatch(ConventionAwareHelper::isProviderType);
    }

    private static boolean isProviderType(Method getter) {
        return Property.class.isAssignableFrom(getter.getReturnType()) || ListProperty.class.isAssignableFrom(getter.getReturnType()) || SetProperty.class.isAssignableFrom(getter.getReturnType()) || MapProperty.class.isAssignableFrom(getter.getReturnType());
    }

    private MappedProperty map(String propertyName, MappedPropertyImpl mapping) {
        if (_propertyNames.getOrDefault(propertyName, false)) {
            throw new InvalidUserDataException("Using convention mapping with Property type is not supported. Use Property#set(...) instead.");
        }
        if (!_propertyNames.containsKey(propertyName)) {
            throw new InvalidUserDataException(
                "You can't map a property that does not exist: propertyName=" + propertyName);
        }

        _mappings.put(propertyName, mapping);
        return mapping;
    }

    public MappedProperty map(String propertyName, final Closure<?> value) {
        return map(propertyName, new MappedPropertyImpl() {
            public Object doGetValue(Convention convention, IConventionAware conventionAwareObject) {
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

    public MappedProperty map(String propertyName, final Callable<?> value) {
        return map(propertyName, new MappedPropertyImpl() {
            public Object doGetValue(Convention convention, IConventionAware conventionAwareObject) {
                return uncheckedCall(value);
            }
        });
    }

    public void propertyMissing(String name, Object value) {
        if (value instanceof Closure) {
            map(name, (Closure) value);
        } else {
            throw new MissingPropertyException(name, getClass());
        }
    }

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
                returnValue = (T) _mappings.get(propertyName).getValue(_convention, _source);
            }
        }
        return returnValue;
    }

    public Convention getConvention() {
        return _convention;
    }

    private static abstract class MappedPropertyImpl implements MappedProperty {
        private boolean haveValue;
        private boolean cache;
        private Object cachedValue;

        public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
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

        abstract Object doGetValue(Convention convention, IConventionAware conventionAwareObject);
    }
}
