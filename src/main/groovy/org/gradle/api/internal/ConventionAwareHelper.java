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

import org.gradle.api.InvalidUserDataException;
import org.gradle.util.ReflectionUtil;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import groovy.lang.GroovyObject;
import groovy.lang.Closure;

/**
 * I would love to have this as a mixin. But Groovy does not support them yet.
 *
 * @author Hans Dockter
 */
public class ConventionAwareHelper {
    private Object convention;

    private Object source;

    private Map conventionMapping = new HashMap();
    private Map conventionMappingCache = new HashMap();

    public ConventionAwareHelper(Object source) {
        this.source = source;
    }

    public Object convention(Object convention, Map conventionMapping) {
        this.convention = convention;
        this.conventionMapping = conventionMapping;
        return source;
    }

    public Object conventionMapping(Map mapping) {
        Iterator keySetIterator = mapping.keySet().iterator();
        while (keySetIterator.hasNext()) {
            String propertyName = (String) keySetIterator.next();
            if (!ReflectionUtil.hasProperty(source, propertyName)) {
                throw new InvalidUserDataException("You can't map a property that does not exists: propertyName= " + propertyName);
            }
        }
        this.conventionMapping.putAll(mapping);
        return source;
    }

    public Object getValue(String propertyName) {
        Object value = ReflectionUtil.getProperty(source, propertyName);
        if (value == null && conventionMapping.keySet().contains(propertyName)) {
            if (!conventionMappingCache.keySet().contains(propertyName)) {
                Object conventionValue = ((Closure) conventionMapping.get(propertyName)).call(new Object[] {convention});
                conventionMappingCache.put(propertyName, conventionValue);
            }
            value = conventionMappingCache.get(propertyName);
        }
        return value;
    }

    public Object getConventionValue(Object internalValue, String propertyName) {
        Object returnValue = internalValue;
        if (internalValue == null && conventionMapping.keySet().contains(propertyName)) {
            if (!conventionMappingCache.keySet().contains(propertyName)) {
                Object conventionValue = ((Closure) conventionMapping.get(propertyName)).call(new Object[] {convention});
                conventionMappingCache.put(propertyName, conventionValue);
            }
            returnValue = conventionMappingCache.get(propertyName);
        }
        return returnValue;
    }

    public Object getConvention() {
        return convention;
    }

    public void setConvention(Object convention) {
        this.convention = convention;
    }

    public Object getSource() {
        return source;
    }

    public void setSource(GroovyObject source) {
        this.source = source;
    }

    public Map getConventionMapping() {
        return conventionMapping;
    }

    public void setConventionMapping(Map conventionMapping) {
        this.conventionMapping = conventionMapping;
    }

    public Map getConventionMappingCache() {
        return conventionMappingCache;
    }

    public void setConventionMappingCache(Map conventionMappingCache) {
        this.conventionMappingCache = conventionMappingCache;
    }
}
