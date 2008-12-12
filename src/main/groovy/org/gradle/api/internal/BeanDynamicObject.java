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

import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovySystem;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link DynamicObject} which uses groovy reflection to provide access to the properties and methods of a bean.
 */
public class BeanDynamicObject extends AbstractDynamicObject {
    private final Object bean;

    public BeanDynamicObject(Object bean) {
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }

    protected String getDisplayName() {
        return bean.toString();
    }

    private MetaClass getMetaClass() {
        if (bean instanceof GroovyObject) {
            return ((GroovyObject) bean).getMetaClass();
        } else {
            return GroovySystem.getMetaClassRegistry().getMetaClass(bean.getClass());
        }
    }

    @Override
    public boolean hasProperty(String name) {
        return getMetaClass().hasProperty(bean, name) != null;
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        MetaProperty property = getMetaClass().hasProperty(bean, name);
        if (property == null) {
            throw propertyMissingException(name);
        }

        if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getGetter() == null) {
            throw new GroovyRuntimeException(String.format(
                    "Cannot get the value of write-only property '%s' on %s.", name, getDisplayName()));
        }
        try {
            return property.getProperty(bean);
        } catch (InvokerInvocationException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        MetaProperty property = getMetaClass().hasProperty(bean, name);
        if (property == null) {
            throw propertyMissingException(name);
        }

        if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getSetter() == null) {
            throw new GroovyRuntimeException(String.format(
                    "Cannot set the value of read-only property '%s' on %s.", name, getDisplayName()));
        }
        try {
            property.setProperty(bean, value);
        } catch (InvokerInvocationException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        List<MetaProperty> classProperties = getMetaClass().getProperties();
        for (MetaProperty metaProperty : classProperties) {
            if (metaProperty.getName().equals("properties")) {
                properties.put("properties", properties);
                continue;
            }
            if (metaProperty instanceof MetaBeanProperty) {
                MetaBeanProperty beanProperty = (MetaBeanProperty) metaProperty;
                if (beanProperty.getGetter() == null) {
                    continue;
                }
            }
            properties.put(metaProperty.getName(), metaProperty.getProperty(bean));
        }
        return properties;
    }

    @Override
    public boolean hasMethod(String name, Object... params) {
        return !getMetaClass().respondsTo(bean, name, params).isEmpty();
    }

    @Override
    public Object invokeMethod(String name, Object... params) throws MissingMethodException {
        MetaMethod method = getMetaClass().getMetaMethod(name, params);
        if (method == null) {
            throw methodMissingException(name, params);
        }

        try {
            return method.invoke(bean, params);
        } catch (InvokerInvocationException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
