/*
 * Copyright 2007 the original author or authors.
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

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.ReflectionUtil;
import org.gradle.util.UncheckedException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Hans Dockter
 */
public class ConventionAwareHelper implements ConventionMapping {
    private Convention convention;

    private IConventionAware source;

    private Map<String, ConventionValue> conventionMapping = new HashMap<String, ConventionValue>();

    public ConventionAwareHelper(IConventionAware source, Convention convention) {
        this.source = source;
        this.convention = convention;
    }

    public MappedProperty map(String propertyName, ConventionValue value) {
        MappedPropertyImpl property = new MappedPropertyImpl(value);
        map(Collections.singletonMap(propertyName, property));
        return property;
    }

    public MappedProperty map(String propertyName, final Closure value) {
        return map(propertyName, new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                switch (value.getMaximumNumberOfParameters()) {
                    case 0:
                        return value.call();
                    case 1:
                        return value.call(convention);
                    default:
                        return value.call(new Object[]{convention, conventionAwareObject});
                }
            }
        });
    }

    public MappedProperty map(String propertyName, final Callable<?> value) {
        return map(propertyName, new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                try {
                    return value.call();
                } catch (Exception e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
        });
    }

    public ConventionMapping map(Map<String, ? extends ConventionValue> mapping) {
        for (Map.Entry<String, ? extends ConventionValue> entry : mapping.entrySet()) {
            String propertyName = entry.getKey();
            if (!ReflectionUtil.hasProperty(source, propertyName)) {
                throw new InvalidUserDataException(
                        "You can't map a property that does not exist: propertyName=" + propertyName);
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("No convention value provided: propertyName= " + propertyName);
            }
        }
        this.conventionMapping.putAll(mapping);
        return this;
    }

    public void propertyMissing(String name, Object value) {
        if (value instanceof Closure) {
            map(name, (Closure) value);
        } else if (value instanceof ConventionValue) {
            map(name, (ConventionValue) value);
        } else {
            throw new MissingPropertyException(name, getClass());
        }
    }

    public Object getConventionValue(String propertyName) {
        Object value = ReflectionUtil.getProperty(source, propertyName);
        return getConventionValue(value, propertyName);
    }

    public <T> T getConventionValue(T internalValue, String propertyName) {
        Object returnValue = internalValue;
        boolean useMapping = internalValue == null
                || internalValue instanceof Collection && ((Collection) internalValue).isEmpty()
                || internalValue instanceof Map && ((Map) internalValue).isEmpty();
        if (useMapping && conventionMapping.keySet().contains(propertyName)) {
            returnValue = conventionMapping.get(propertyName).getValue(convention, source);
        }
        return (T) returnValue;
    }

    public <T> T getConventionValue(T actualValue, String propertyName, boolean isExplicitValue) {
        if (isExplicitValue) {
            return actualValue;
        }
        return getConventionValue(actualValue, propertyName);
    }

    public Convention getConvention() {
        return convention;
    }

    public void setConvention(Convention convention) {
        this.convention = convention;
    }

    public IConventionAware getSource() {
        return source;
    }

    public void setSource(IConventionAware source) {
        this.source = source;
    }

    public Map getConventionMapping() {
        return conventionMapping;
    }

    public void setConventionMapping(Map conventionMapping) {
        this.conventionMapping = conventionMapping;
    }

    private static class MappedPropertyImpl implements MappedProperty, ConventionValue {
        private final ConventionValue value;
        private boolean haveValue;
        private boolean cache;
        private Object cachedValue;

        private MappedPropertyImpl(ConventionValue value) {
            this.value = value;
        }

        public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
            if (!cache) {
                return value.getValue(convention, conventionAwareObject);
            }
            if (!haveValue) {
                cachedValue = value.getValue(convention, conventionAwareObject);
                haveValue = true;
            }
            return cachedValue;
        }

        public void cache() {
            cache = true;
            cachedValue = null;
        }
    }
}
