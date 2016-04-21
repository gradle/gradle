/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.*;
import groovy.lang.MissingMethodException;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.Nullable;
import org.gradle.api.internal.coerce.MethodArgumentsTransformer;
import org.gradle.api.internal.coerce.PropertySetTransformer;
import org.gradle.api.internal.coerce.StringToEnumTransformer;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A {@link DynamicObject} which uses groovy reflection to provide access to the properties and methods of a bean.
 */
public class BeanDynamicObject extends AbstractDynamicObject {
    private static final Method META_PROP_METHOD;
    private static final Field MISSING_PROPERTY_GET_METHOD;
    private final Object bean;
    private final boolean includeProperties;
    private final DynamicObject delegate;
    private final boolean implementsMissing;

    // NOTE: If this guy starts caching internally, consider sharing an instance
    private final MethodArgumentsTransformer argsTransformer = StringToEnumTransformer.INSTANCE;
    private final PropertySetTransformer propertySetTransformer = StringToEnumTransformer.INSTANCE;

    static {
        try {
            META_PROP_METHOD = MetaClassImpl.class.getDeclaredMethod("getMetaProperty", String.class, boolean.class);
            META_PROP_METHOD.setAccessible(true);
            MISSING_PROPERTY_GET_METHOD = MetaClassImpl.class.getDeclaredField("propertyMissingGet");
            MISSING_PROPERTY_GET_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public BeanDynamicObject(Object bean) {
        this(bean, true);
    }

    private BeanDynamicObject(Object bean, boolean includeProperties) {
        this(bean, includeProperties, true);
    }

    private BeanDynamicObject(Object bean, boolean includeProperties, boolean implementsMissing) {
        if (bean == null) {
            throw new IllegalArgumentException("Value is null");
        }
        this.bean = bean;
        this.includeProperties = includeProperties;
        this.implementsMissing = implementsMissing;
        this.delegate = determineDelegate(bean);
    }

    public DynamicObject determineDelegate(Object bean) {
        if (bean instanceof DynamicObject || bean instanceof DynamicObjectAware || !(bean instanceof GroovyObject)) {
            return new MetaClassAdapter();
        } else {
            return new GroovyObjectAdapter();
        }
    }

    public BeanDynamicObject withNoProperties() {
        return new BeanDynamicObject(bean, false);
    }

    public BeanDynamicObject withNotImplementsMissing() {
        return new BeanDynamicObject(bean, includeProperties, false);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
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
    public boolean isMayImplementMissingMethods() {
        return implementsMissing && delegate.isMayImplementMissingMethods();
    }

    @Override
    public boolean hasProperty(String name) {
        return delegate.hasProperty(name);
    }

    @Override
    public void getProperty(String name, GetPropertyResult result) {
        delegate.getProperty(name, result);
    }

    @Override
    public void setProperty(String name, Object value, SetPropertyResult result) {
        delegate.setProperty(name, value, result);
    }

    @Override
    public Map<String, ?> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        return delegate.hasMethod(name, arguments);
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
        try {
            return delegate.invokeMethod(name, arguments);
        } catch (MissingMethodException e) {
            if (e.isStatic() || !e.getMethod().equals(name) || !Arrays.equals(e.getArguments(), arguments)) {
                throw e;
            } else {
                Object[] transformedArguments = argsTransformer.transform(bean, name, arguments);
                if (transformedArguments != arguments) {
                    return delegate.invokeMethod(name, transformedArguments);
                } else {
                    throw e;
                }
            }
        }
    }

    private class MetaClassAdapter extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return BeanDynamicObject.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return includeProperties && getMetaClass().hasProperty(bean, name) != null;
        }

        @Override
        public void getProperty(String name, GetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();

            // First look for a property known to the meta-class
            MetaProperty property = lookupProperty(metaClass, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getGetter() == null) {
                    throw new GroovyRuntimeException(String.format(
                            "Cannot get the value of write-only property '%s' on %s.", name, getDisplayName()));
                }

                try {
                    result.result(property.getProperty(bean));
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
                return;
            }

            if (!implementsMissing) {
                return;
            }

            // Fall back to propertyMissing, if available
            MetaMethod propertyMissing = findPropertyMissingMethod(metaClass);
            if (propertyMissing != null) {
                try {
                    result.result(propertyMissing.invoke(bean, new Object[]{name}));
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                }
            } else if (bean instanceof GroovyObject && !(bean instanceof DynamicObjectAware)) {
                try {
                    result.result(((GroovyObject) bean).getProperty(name));
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                }
            }
        }

        @Nullable
        private MetaMethod findPropertyMissingMethod(MetaClass metaClass) {
            if (metaClass instanceof MetaClassImpl) {
                // Reach into meta class to avoid lookup
                try {
                    return (MetaMethod) MISSING_PROPERTY_GET_METHOD.get(metaClass);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Query the declared methods of the meta class
            for (MetaMethod method : metaClass.getMethods()) {
                if (method.getName().equals("propertyMissing") && method.getParameterTypes().length == 1) {
                    return method;
                }
            }
            return null;
        }

        @Nullable
        private MetaProperty lookupProperty(MetaClass metaClass, String name) {
            if (metaClass instanceof MetaClassImpl) {
                // MetaClass.getMetaProperty(name) is very expensive when the property is not known. Instead, reach into the meta class to call a much more efficient lookup method
                try {
                    return (MetaProperty) META_PROP_METHOD.invoke(metaClass, name, false);
                } catch (Throwable e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Some other meta-class implementation - fall back to the public API
            return metaClass.getMetaProperty(name);
        }

        @Override
        public void setProperty(final String name, Object value, SetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();
            MetaProperty property = metaClass.hasProperty(bean, name);
            if (property == null) {
                if (implementsMissing) {
                    try {
                        setOpaqueProperty(name, value, metaClass);
                        result.found();
                    } catch (MissingPropertyException e) {
                        if (!name.equals(e.getProperty())) {
                            throw e;
                        }
                    }
                }
                return;
            }

            if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getSetter() == null) {
                throw new ReadOnlyPropertyException(name, bean.getClass()) {
                    @Override
                    public String getMessage() {
                        return String.format("Cannot set the value of read-only property '%s' on %s.", name,
                                getDisplayName());
                    }
                };
            }
            try {
                value = propertySetTransformer.transformValue(bean, property, value);
                metaClass.setProperty(bean, name, value);
                result.found();
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            }
        }

        protected void setOpaqueProperty(String name, Object value, MetaClass metaClass) {
            metaClass.invokeMissingProperty(bean, name, value, false);
        }

        @Override
        public Map<String, ?> getProperties() {
            if (!includeProperties) {
                return Collections.emptyMap();
            }

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
        public boolean hasMethod(final String name, final Object... arguments) {
            return !getMetaClass().respondsTo(bean, name, arguments).isEmpty();
        }

        @Override
        public Object invokeMethod(final String name, final Object... arguments) throws MissingMethodException {
            try {
                return getMetaClass().invokeMethod(bean, name, arguments);
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            }
        }

        @Override
        public boolean isMayImplementMissingMethods() {
            return true;
        }

    }

    /*
       The GroovyObject interface defines dynamic property and dynamic method methods. Implementers
       are free to implement their own logic in these methods which makes it invisible to the metaclass.

       The most notable case of this is Closure.

       So in this case we use these methods directly on the GroovyObject in case it does implement logic at this level.
     */
    private class GroovyObjectAdapter extends MetaClassAdapter {
        private final GroovyObject groovyObject = (GroovyObject) bean;

        @Override
        protected void setOpaqueProperty(String name, Object value, MetaClass metaClass) {
            groovyObject.setProperty(name, value);
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
            try {
                return groovyObject.invokeMethod(name, arguments);
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            }
        }
    }
}
