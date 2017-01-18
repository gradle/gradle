/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.metaobject;

import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.runtime.metaclass.MultipleSetterProperty;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.gradle.api.Nullable;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.coerce.MethodArgumentsTransformer;
import org.gradle.api.internal.coerce.PropertySetTransformer;
import org.gradle.api.internal.coerce.StringToEnumTransformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link DynamicObject} which uses groovy reflection to provide access to the properties and methods of a bean.
 *
 * <p>Uses some deep hacks to avoid some expensive reflections and the use of exceptions when a particular property or method cannot be found,
 * for example, when a decorated object is used as the delegate of a configuration closure. Also uses some hacks to insert some customised type
 * coercion and error reporting. Enjoy.
 */
public class BeanDynamicObject extends AbstractDynamicObject {
    private static final Method META_PROP_METHOD;
    private static final Field MISSING_PROPERTY_GET_METHOD;
    private static final Field MISSING_PROPERTY_SET_METHOD;
    private static final Field MISSING_METHOD_METHOD;
    private final Object bean;
    private final boolean includeProperties;
    private final MetaClassAdapter delegate;
    private final boolean implementsMissing;
    @Nullable
    private final Class<?> publicType;

    private final MethodArgumentsTransformer argsTransformer;
    private final PropertySetTransformer propertySetTransformer;

    static {
        try {
            META_PROP_METHOD = MetaClassImpl.class.getDeclaredMethod("getMetaProperty", String.class, boolean.class);
            META_PROP_METHOD.setAccessible(true);
            MISSING_PROPERTY_GET_METHOD = MetaClassImpl.class.getDeclaredField("propertyMissingGet");
            MISSING_PROPERTY_GET_METHOD.setAccessible(true);
            MISSING_PROPERTY_SET_METHOD = MetaClassImpl.class.getDeclaredField("propertyMissingSet");
            MISSING_PROPERTY_SET_METHOD.setAccessible(true);
            MISSING_METHOD_METHOD = MetaClassImpl.class.getDeclaredField("methodMissing");
            MISSING_METHOD_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public BeanDynamicObject(Object bean) {
        this(bean, null, true, true, StringToEnumTransformer.INSTANCE, StringToEnumTransformer.INSTANCE);
    }

    public BeanDynamicObject(Object bean, @Nullable Class<?> publicType) {
        this(bean, publicType, true, true, StringToEnumTransformer.INSTANCE, StringToEnumTransformer.INSTANCE);
    }

    BeanDynamicObject(Object bean, @Nullable Class<?> publicType, boolean includeProperties, boolean implementsMissing, PropertySetTransformer propertySetTransformer, MethodArgumentsTransformer methodArgumentsTransformer) {
        if (bean == null) {
            throw new IllegalArgumentException("Value is null");
        }
        this.bean = bean;
        this.publicType = publicType;
        this.includeProperties = includeProperties;
        this.implementsMissing = implementsMissing;
        this.propertySetTransformer = propertySetTransformer;
        this.argsTransformer = methodArgumentsTransformer;
        this.delegate = determineDelegate(bean);
    }

    public MetaClassAdapter determineDelegate(Object bean) {
        if (bean instanceof Class) {
            return new ClassAdapter((Class<?>) bean);
        } else if (bean instanceof Map) {
            return new MapAdapter();
        } else if (bean instanceof DynamicObject || bean instanceof DynamicObjectAware || !(bean instanceof GroovyObject)) {
            return new MetaClassAdapter();
        }
        return new GroovyObjectAdapter();
    }

    public BeanDynamicObject withNoProperties() {
        return new BeanDynamicObject(bean, publicType, false, implementsMissing, propertySetTransformer, argsTransformer);
    }

    public BeanDynamicObject withNotImplementsMissing() {
        return new BeanDynamicObject(bean, publicType, includeProperties, false, propertySetTransformer, argsTransformer);
    }

    @Override
    public String getDisplayName() {
        return bean.toString();
    }

    @Nullable
    @Override
    public Class<?> getPublicType() {
        return publicType != null ? publicType : bean.getClass();
    }

    @Override
    public boolean hasUsefulDisplayName() {
        return !JavaReflectionUtil.hasDefaultToString(bean);
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
    public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
        delegate.invokeMethod(name, result, arguments);
    }

    private class MetaClassAdapter {
        protected String getDisplayName() {
            return BeanDynamicObject.this.getDisplayName();
        }

        public boolean hasProperty(String name) {
            if (!includeProperties) {
                return false;
            }
            if (lookupProperty(getMetaClass(), name) != null) {
                return true;
            }
            if (bean instanceof PropertyMixIn) {
                PropertyMixIn propertyMixIn = (PropertyMixIn) bean;
                return propertyMixIn.getAdditionalProperties().hasProperty(name);
            }
            return false;
        }

        public void getProperty(String name, GetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();

            // First look for a property known to the meta-class
            MetaProperty property = lookupProperty(metaClass, name);
            if (property != null) {
                if (property instanceof MetaBeanProperty && ((MetaBeanProperty) property).getGetter() == null) {
                    throw getWriteOnlyProperty(name);
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

            if (bean instanceof PropertyMixIn) {
                PropertyMixIn propertyMixIn = (PropertyMixIn) bean;
                propertyMixIn.getAdditionalProperties().getProperty(name, result);
                // Do not check for opaque properties when implementing PropertyMixIn, as this is expensive
                return;
            }

            if (!implementsMissing) {
                return;
            }

            // Fall back to propertyMissing, if available
            MetaMethod propertyMissing = findGetPropertyMissingMethod(metaClass);
            if (propertyMissing != null) {
                try {
                    result.result(propertyMissing.invoke(bean, new Object[]{name}));
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                    // Else, ignore
                }
            }

            getOpaqueProperty(name, result);
        }

        protected void getOpaqueProperty(String name, GetPropertyResult result) {
        }

        @Nullable
        private MetaMethod findGetPropertyMissingMethod(MetaClass metaClass) {
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
        private MetaMethod findSetPropertyMissingMethod(MetaClass metaClass) {
            if (metaClass instanceof MetaClassImpl) {
                // Reach into meta class to avoid lookup
                try {
                    return (MetaMethod) MISSING_PROPERTY_SET_METHOD.get(metaClass);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Query the declared methods of the meta class
            for (MetaMethod method : metaClass.getMethods()) {
                if (method.getName().equals("propertyMissing") && method.getParameterTypes().length == 2) {
                    return method;
                }
            }
            return null;
        }

        @Nullable
        private MetaMethod findMethodMissingMethod(MetaClass metaClass) {
            if (metaClass instanceof MetaClassImpl) {
                // Reach into meta class to avoid lookup
                try {
                    return (MetaMethod) MISSING_METHOD_METHOD.get(metaClass);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            // Query the declared methods of the meta class
            for (MetaMethod method : metaClass.getMethods()) {
                if (method.getName().equals("methodMissing") && method.getParameterTypes().length == 2) {
                    return method;
                }
            }
            return null;
        }

        @Nullable
        protected MetaProperty lookupProperty(MetaClass metaClass, String name) {
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

        public void setProperty(final String name, Object value, SetPropertyResult result) {
            if (!includeProperties) {
                return;
            }

            MetaClass metaClass = getMetaClass();
            MetaProperty property = lookupProperty(metaClass, name);
            if (property != null) {
                if (property instanceof MultipleSetterProperty) {
                    // Invoke the setter method, to pick up type coercion
                    String setterName = MetaProperty.getSetterName(property.getName());
                    InvokeMethodResult setterResult = new InvokeMethodResult();
                    invokeMethod(setterName, setterResult, value);
                    if (setterResult.isFound()) {
                        result.found();
                        return;
                    }
                } else {
                    if (property instanceof MetaBeanProperty) {
                        MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;
                        if (metaBeanProperty.getSetter() == null) {
                            if (metaBeanProperty.getField() == null) {
                                throw setReadOnlyProperty(name);
                            }
                            value = propertySetTransformer.transformValue(metaBeanProperty.getField().getType(), value);
                            metaBeanProperty.getField().setProperty(bean, value);
                        } else {
                            // Coerce the value to the type accepted by the property setter and invoke the setter directly
                            Class setterType = metaBeanProperty.getSetter().getParameterTypes()[0].getTheClass();
                            value = propertySetTransformer.transformValue(setterType, value);
                            value = DefaultTypeTransformation.castToType(value, setterType);
                            metaBeanProperty.getSetter().invoke(bean, new Object[]{value});
                        }
                    } else {
                        // Coerce the value to the property type, if known
                        value = propertySetTransformer.transformValue(property.getType(), value);
                        property.setProperty(bean, value);
                    }
                    result.found();
                    return;
                }
            }

            if (bean instanceof PropertyMixIn) {
                PropertyMixIn propertyMixIn = (PropertyMixIn) bean;
                propertyMixIn.getAdditionalProperties().setProperty(name, value, result);
                // When implementing PropertyMixIn, do not check for opaque properties, as this can be expensive
                return;
            }

            if (!implementsMissing) {
                return;
            }

            MetaMethod propertyMissingMethod = findSetPropertyMissingMethod(metaClass);
            if (propertyMissingMethod != null) {
                try {
                    propertyMissingMethod.invoke(bean, new Object[]{name, value});
                    result.found();
                    return;
                } catch (MissingPropertyException e) {
                    if (!name.equals(e.getProperty())) {
                        throw e;
                    }
                    // Else, ignore
                }
            }

            setOpaqueProperty(metaClass, name, value, result);
        }

        protected void setOpaqueProperty(MetaClass metaClass, String name, Object value, SetPropertyResult result) {
        }

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
            if (bean instanceof PropertyMixIn) {
                PropertyMixIn propertyMixIn = (PropertyMixIn) bean;
                properties.putAll(propertyMixIn.getAdditionalProperties().getProperties());
            }
            getOpaqueProperties(properties);
            return properties;
        }

        protected void getOpaqueProperties(Map<String, Object> properties) {
        }

        public boolean hasMethod(final String name, final Object... arguments) {
            if (lookupMethod(getMetaClass(), name, inferTypes(arguments)) != null) {
                return true;
            }
            if (bean instanceof MethodMixIn) {
                MethodMixIn methodMixIn = (MethodMixIn) bean;
                return methodMixIn.getAdditionalMethods().hasMethod(name, arguments);
            }
            return false;
        }

        private Class[] inferTypes(Object... arguments) {
            if (arguments == null || arguments.length == 0) {
                return MetaClassHelper.EMPTY_CLASS_ARRAY;
            }
            Class[] classes = new Class[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object argType = arguments[i];
                if (argType == null) {
                    classes[i] = null;
                } else {
                    classes[i] = argType.getClass();
                }
            }
            return classes;
        }

        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            MetaClass metaClass = getMetaClass();
            MetaMethod metaMethod = lookupMethod(metaClass, name, inferTypes(arguments));
            if (metaMethod != null) {
                result.result(metaMethod.doMethodInvoke(bean, arguments));
                return;
            }

            List<MetaMethod> metaMethods = metaClass.respondsTo(bean, name);
            for (MetaMethod method : metaMethods) {
                if (method.getParameterTypes().length != arguments.length) {
                    continue;
                }
                Object[] transformed = argsTransformer.transform(method.getParameterTypes(), arguments);
                if (transformed == arguments) {
                    continue;
                }
                result.result(method.doMethodInvoke(bean, transformed));
                return;
            }

            if (bean instanceof MethodMixIn) {
                MethodMixIn methodMixIn = (MethodMixIn) bean;
                methodMixIn.getAdditionalMethods().invokeMethod(name, result, arguments);
                // If implements MethodMixIn, do not attempt to locate opaque method, as this is expensive
                return;
            }

            if (!implementsMissing) {
                return;
            }

            invokeOpaqueMethod(metaClass, name, arguments, result);
        }

        @Nullable
        protected MetaMethod lookupMethod(MetaClass metaClass, String name, Class[] arguments) {
            return metaClass.getMetaMethod(name, arguments);
        }

        protected void invokeOpaqueMethod(MetaClass metaClass, String name, Object[] arguments, InvokeMethodResult result) {
            MetaMethod methodMissingMethod = findMethodMissingMethod(metaClass);
            if (methodMissingMethod != null) {
                try {
                    try {
                        result.result(methodMissingMethod.invoke(bean, new Object[] {name, arguments}));
                    } catch (InvokerInvocationException e) {
                        if (e.getCause() instanceof MissingMethodException) {
                            throw (MissingMethodException) e.getCause();
                        }
                        throw e;
                    }
                } catch (MissingMethodException e) {
                    if (!e.getMethod().equals(name) || !Arrays.equals(e.getArguments(), arguments)) {
                        throw e;
                    }
                    // Ignore
                }
            }
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
        protected void getOpaqueProperty(String name, GetPropertyResult result) {
            try {
                result.result(groovyObject.getProperty(name));
            } catch (MissingPropertyException e) {
                if (!name.equals(e.getProperty())) {
                    throw e;
                }
                // Else, ignore
            }
        }

        @Override
        protected void setOpaqueProperty(MetaClass metaClass, String name, Object value, SetPropertyResult result) {
            try {
                groovyObject.setProperty(name, value);
                result.found();
            } catch (MissingPropertyException e) {
                if (!name.equals(e.getProperty())) {
                    throw e;
                }
                // Else, ignore
            }
        }

        @Override
        protected void invokeOpaqueMethod(MetaClass metaClass, String name, Object[] arguments, InvokeMethodResult result) {
            try {
                try {
                    result.result(groovyObject.invokeMethod(name, arguments));
                } catch (InvokerInvocationException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw e;
                }
            } catch (MissingMethodException e) {
                if (!e.getMethod().equals(name) || !Arrays.equals(e.getArguments(), arguments)) {
                    throw e;
                }
                // Else, ignore
            }
        }
    }

    private class MapAdapter extends MetaClassAdapter {
        Map<String, Object> map = (Map<String, Object>) bean;

        @Override
        public boolean hasProperty(String name) {
            return map.containsKey(name) || super.hasProperty(name);
        }

        @Override
        protected void getOpaqueProperty(String name, GetPropertyResult result) {
            result.result(map.get(name));
        }

        @Override
        protected void getOpaqueProperties(Map<String, Object> properties) {
            properties.putAll(map);
        }

        @Override
        protected void setOpaqueProperty(MetaClass metaClass, String name, Object value, SetPropertyResult result) {
            map.put(name, value);
            result.found();
        }
    }

    private class ClassAdapter extends MetaClassAdapter {
        private final MetaClass classMetaData;

        ClassAdapter(Class<?> cl) {
            classMetaData = GroovySystem.getMetaClassRegistry().getMetaClass(cl);
        }

        @Nullable
        @Override
        protected MetaProperty lookupProperty(MetaClass metaClass, String name) {
            MetaProperty metaProperty = super.lookupProperty(metaClass, name);
            if (metaProperty != null) {
                return metaProperty;
            }
            metaProperty = classMetaData.getMetaProperty(name);
            if (metaProperty != null && Modifier.isStatic(metaProperty.getModifiers())) {
                return metaProperty;
            }
            return null;
        }

        @Nullable
        @Override
        protected MetaMethod lookupMethod(MetaClass metaClass, String name, Class[] arguments) {
            MetaMethod metaMethod = super.lookupMethod(metaClass, name, arguments);
            if (metaMethod != null) {
                return metaMethod;
            }
            metaMethod = classMetaData.getMetaMethod(name, arguments);
            if (metaMethod != null && Modifier.isStatic(metaMethod.getModifiers())) {
                return metaMethod;
            }
            return null;
        }
    }
}
