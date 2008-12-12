/*
 * Copyright 2008 the original author or authors.
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

import groovy.lang.MissingPropertyException;
import org.gradle.api.plugins.Convention;

import java.util.HashMap;
import java.util.Map;

public class DynamicObjectHelper extends AbstractDynamicObject {

    private final BeanDynamicObject delegateObject;
    private final boolean includeDelegateObjectProperties;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    private DynamicObject parent;
    private Convention convention;

    public DynamicObjectHelper(Object delegateObject) {
        this(delegateObject, true);
    }

    public DynamicObjectHelper(Object delegateObject, boolean includeDelegateObjectProperties) {
        this.includeDelegateObjectProperties = includeDelegateObjectProperties;
        this.delegateObject = new BeanDynamicObject(delegateObject);
    }

    protected String getDisplayName() {
        return delegateObject.getDisplayName();
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public DynamicObject getParent() {
        return parent;
    }

    public void setParent(DynamicObject parent) {
        this.parent = parent;
    }

    public Convention getConvention() {
        return convention;
    }

    public void setConvention(Convention convention) {
        this.convention = convention;
    }

    public Object getDelegateObject() {
        return delegateObject.getBean();
    }

    public boolean hasProperty(String name) {
        if (includeDelegateObjectProperties && delegateObject.hasProperty(name)) {
            return true;
        }
        if (convention != null && convention.hasProperty(name)) {
            return true;
        }
        if (parent != null && parent.hasProperty(name)) {
            return true;
        }
        return additionalProperties.containsKey(name);
    }

    public Object getProperty(String name) {
        if (includeDelegateObjectProperties && delegateObject.hasProperty(name)) {
            return delegateObject.getProperty(name);
        }
        if (additionalProperties.containsKey(name)) {
            return additionalProperties.get(name);
        }
        if (convention != null && convention.hasProperty(name)) {
            return convention.getProperty(name);
        }
        if (parent != null) {
            return parent.getProperty(name);
        }
        throw propertyMissingException(name);
    }

    public void setProperty(String name, Object value) {
        if (includeDelegateObjectProperties && delegateObject.hasProperty(name)) {
            delegateObject.setProperty(name, value);
        }
        if (convention != null && convention.hasProperty(name)) {
            convention.setProperty(name, value);
        }
        additionalProperties.put(name, value);
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(additionalProperties);
        if (parent != null) {
            properties.putAll(parent.getProperties());
        }
        if (convention != null) {
            properties.putAll(convention.getAllProperties());
        }
        if (includeDelegateObjectProperties) {
            properties.putAll(delegateObject.getProperties());
        }
        properties.put("properties", properties);
        return properties;
    }

    public boolean hasMethod(String name, Object... params) {
        if (delegateObject.hasMethod(name, params)) {
            return true;
        }
        if (convention != null && convention.hasMethod(name, params)) {
            return true;
        }
        if (parent != null && parent.hasMethod(name, params)) {
            return true;
        }
        return false;
    }

    public Object invokeMethod(String name, Object... params) {
        if (delegateObject.hasMethod(name, params)) {
            return delegateObject.invokeMethod(name, params);
        }
        if (convention != null && convention.hasMethod(name, params)) {
            return convention.invokeMethod(name, params);
        }
        if (parent != null && parent.hasMethod(name, params)) {
            return parent.invokeMethod(name, params);
        }
        throw methodMissingException(name, params);
    }

    public DynamicObject getInheritable() {
        DynamicObjectHelper helper = new DynamicObjectHelper(delegateObject.getBean(), false);
        helper.parent = parent;
        helper.convention = convention;
        helper.additionalProperties = additionalProperties;
        return new HelperBackedDynamicObject(helper);
    }
}

class HelperBackedDynamicObject implements DynamicObject {
    private final DynamicObjectHelper helper;

    public HelperBackedDynamicObject(DynamicObjectHelper helper) {
        this.helper = helper;
    }

    public void setProperty(String name, Object value) {
        throw new MissingPropertyException(String.format("Could not find property '%s' inherited from %s.", name,
                helper.getDelegateObject()));
    }

    public boolean hasProperty(String name) {
        return helper.hasProperty(name);
    }

    public Object getProperty(String name) {
        return helper.getProperty(name);
    }

    public Map<String, Object> getProperties() {
        return helper.getProperties();
    }

    public boolean hasMethod(String name, Object... params) {
        return helper.hasMethod(name, params);
    }

    public Object invokeMethod(String name, Object... params) {
        return helper.invokeMethod(name, params);
    }
}
