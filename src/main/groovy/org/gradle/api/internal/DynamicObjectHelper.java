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

import org.gradle.api.plugins.Convention;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import groovy.lang.MetaClass;
import groovy.lang.GroovyObject;
import groovy.lang.MetaProperty;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MissingPropertyException;
import groovy.lang.GroovySystem;
import groovy.lang.MetaMethod;

// todo - figure out how to get this to implement DynamicObject
public class DynamicObjectHelper {

    private final Object delegateObject;
    private final boolean includeDelegateObjectProperties;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    private DynamicObject parent;
    private Convention convention;

    public DynamicObjectHelper(Object delegateObject) {
        this(delegateObject, true);
    }

    public DynamicObjectHelper(Object delegateObject, boolean includeDelegateObjectProperties) {
        this.includeDelegateObjectProperties = includeDelegateObjectProperties;
        this.delegateObject = delegateObject;
    }

    private MetaClass getMetaClass() {
        if (delegateObject instanceof GroovyObject) {
            return ((GroovyObject) delegateObject).getMetaClass();
        } else {
            return GroovySystem.getMetaClassRegistry().getMetaClass(delegateObject.getClass());
        }
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
        return delegateObject;
    }

    public boolean hasObjectProperty(String name) {
        if (includeDelegateObjectProperties && getMetaClass().hasProperty(delegateObject, name) != null) {
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

    public Object getObjectProperty(String name) {
        if (includeDelegateObjectProperties) {
            MetaProperty property = getMetaClass().hasProperty(delegateObject, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getGetter() == null) {
                    throw new GroovyRuntimeException(String.format(
                            "Cannot get the value of write-only property '%s' on %s.", name, delegateObject));
                }
                try {
                    return property.getProperty(delegateObject);
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
            }
        }
        if (additionalProperties.containsKey(name)) {
            return additionalProperties.get(name);
        }
        if (convention != null && convention.hasProperty(name)) {
            return convention.getProperty(name);
        }
        if (parent != null) {
            return parent.property(name);
        }
        throw new MissingPropertyException(String.format("Could not find property '%s' on %s.", name, delegateObject));
    }

    public void setObjectProperty(String name, Object value) {
        if (includeDelegateObjectProperties) {
            MetaProperty property = getMetaClass().hasProperty(delegateObject, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getSetter() == null) {
                    throw new GroovyRuntimeException(String.format(
                            "Cannot set the value of read-only property '%s' on %s.", name, delegateObject));
                }
                try {
                    property.setProperty(delegateObject, value);
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
            }
        }

        if (convention != null && convention.hasProperty(name)) {
            convention.setProperty(name, value);
        }
        additionalProperties.put(name, value);
    }

    public Map<String, Object> allProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(additionalProperties);
        if (parent != null) {
            properties.putAll(parent.properties());
        }
        if (convention != null) {
            properties.putAll(convention.getAllProperties());
        }
        if (includeDelegateObjectProperties) {
            List<MetaProperty> classProperties = getMetaClass().getProperties();
            for (MetaProperty metaProperty : classProperties) {
                if (metaProperty instanceof MetaBeanProperty) {
                    MetaBeanProperty beanProperty = (MetaBeanProperty) metaProperty;
                    if (beanProperty.getGetter() == null) {
                        continue;
                    }
                }
                properties.put(metaProperty.getName(), metaProperty.getProperty(delegateObject));
            }
        }
        return properties;
    }

    public boolean hasObjectMethod(String name, Object... params) {
        if (!getMetaClass().respondsTo(delegateObject, name, params).isEmpty()) {
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

    public Object invokeObjectMethod(String name, Object... params) {
        MetaMethod method = getMetaClass().getMetaMethod(name, params);
        if (method != null) {
            try {
                return method.invoke(delegateObject, params);
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            }
        }
        if (convention != null && convention.hasMethod(name, params)) {
            return convention.invokeMethod(name, params);
        }
        if (parent != null && parent.hasMethod(name, params)) {
            return parent.invokeMethod(name, params);
        }
        throw new MissingMethodException(delegateObject, name, params);
    }

    public DynamicObject getInheritable() {
        DynamicObjectHelper helper = new DynamicObjectHelper(delegateObject, false);
        helper.parent = parent;
        helper.convention = convention;
        helper.additionalProperties = additionalProperties;
        return new HelperBackedDynamicObject(helper);
    }
}

class HelperBackedDynamicObject implements DynamicObject {
    final DynamicObjectHelper helper;

    public HelperBackedDynamicObject(DynamicObjectHelper helper) {
        this.helper = helper;
    }

    public void setProperty(String name, Object value) {
        throw new MissingPropertyException(String.format("Could not find property '%s' inherited from %s.", name,
                helper.getDelegateObject()));
    }

    public boolean hasProperty(String name) {
        return helper.hasObjectProperty(name);
    }

    public Object property(String name) {
        return helper.getObjectProperty(name);
    }

    public Map<String, Object> properties() {
        return helper.allProperties();
    }

    public boolean hasMethod(String name, Object... params) {
        return helper.hasObjectMethod(name, params);
    }

    public Object invokeMethod(String name, Object... params) {
        return helper.invokeObjectMethod(name, params);
    }
}

class MissingMethodException extends groovy.lang.MissingMethodException {
    private final Object delegateObject;

    public MissingMethodException(Object object, String name, Object... arguments) {
        super(name, object.getClass(), arguments);
        delegateObject = object;
    }

    public String getMessage() {
        return String.format("Could not find method %s() for arguments %s on %s.", getMethod(), Arrays.toString(
                getArguments()), delegateObject);
    }
}
