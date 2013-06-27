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
import org.codehaus.groovy.reflection.ParameterTypes;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.gradle.api.Transformer;
import org.gradle.api.internal.coerce.MethodArgumentsTransformer;
import org.gradle.api.internal.coerce.TypeCoercingMethodArgumentsTransformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.JavaMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.gradle.util.CollectionUtils.*;

/**
 * A {@link DynamicObject} which uses groovy reflection to provide access to the properties and methods of a bean.
 */
public class BeanDynamicObject extends AbstractDynamicObject {

    private final Object bean;
    private final boolean includeProperties;
    private final DynamicObject delegate;
    private final boolean implementsMissing;

    // NOTE: If this guy starts caching internally, consider sharing an instance
    private final MethodArgumentsTransformer argsTransformer = new TypeCoercingMethodArgumentsTransformer();

    public BeanDynamicObject(Object bean) {
        this(bean, true);
    }

    private BeanDynamicObject(Object bean, boolean includeProperties) {
        this(bean, includeProperties, true);
    }

    private BeanDynamicObject(Object bean, boolean includeProperties, boolean implementsMissing) {
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
    public boolean isMayImplementMissingProperties() {
        return implementsMissing && includeProperties && delegate.isMayImplementMissingProperties();
    }

    @Override
    public boolean hasProperty(String name) {
        return delegate.hasProperty(name);
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        return delegate.getProperty(name);
    }

    @Override
    public void setProperty(final String name, Object value) throws MissingPropertyException {
        delegate.setProperty(name, value);
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
        // Maybe transform the arguments before calling the method (e.g. type coercion)
        arguments = argsTransformer.transform(bean, name, arguments);
        return delegate.invokeMethod(name, arguments);
    }

    private class MetaClassAdapter implements DynamicObject {

        public boolean hasProperty(String name) {
            return includeProperties && getMetaClass().hasProperty(bean, name) != null;
        }

        public Object getProperty(String name) throws MissingPropertyException {
            if (!includeProperties) {
                throw propertyMissingException(name);
            }

            MetaProperty property = getMetaClass().hasProperty(bean, name);
            if (property == null) {
                return getMetaClass().invokeMissingProperty(bean, name, null, true);
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

        public void setProperty(final String name, Object value) throws MissingPropertyException {
            if (!includeProperties) {
                throw propertyMissingException(name);
            }

            MetaClass metaClass = getMetaClass();
            MetaProperty property = metaClass.hasProperty(bean, name);
            if (property == null) {
                getMetaClass().invokeMissingProperty(bean, name, null, false);
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

                // Attempt type coercion before trying to set the property
                value = argsTransformer.transform(bean, MetaProperty.getSetterName(name), value)[0];

                metaClass.setProperty(bean, name, value);
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            }
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
            return properties;
        }

        public boolean hasMethod(final String name, final Object... arguments) {
            boolean respondsTo = !getMetaClass().respondsTo(bean, name, arguments).isEmpty();
            if (respondsTo) {
                return true;
            } else {
                Method method = JavaReflectionUtil.findMethod(bean.getClass(), new Spec<Method>() {
                    public boolean isSatisfiedBy(Method potentialMethod) {
                        if (Modifier.isPrivate(potentialMethod.getModifiers()) && potentialMethod.getName().equals(name)) {
                            ParameterTypes parameterTypes = new ParameterTypes(potentialMethod.getParameterTypes());
                            return parameterTypes.isValidMethod(arguments);
                        } else {
                            return false;
                        }
                    }
                });

                return method != null;
            }
        }

        public Object invokeMethod(final String name, final Object... arguments) throws MissingMethodException {
            try {
                return getMetaClass().invokeMethod(bean, name, arguments);
            } catch (InvokerInvocationException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw e;
            } catch (MissingMethodException e) {
                // The method may be private, in which case getMetaClass().invokeMethod won't find it.
                // We have to do this ourselves.
                // We tried the "groovy" way first as it's likely to be faster than us at dispatch.

                List<Method> methods = JavaReflectionUtil.findAllMethods(bean.getClass(), new Spec<Method>() {
                    public boolean isSatisfiedBy(Method potentialMethod) {
                        if (Modifier.isPrivate(potentialMethod.getModifiers()) && potentialMethod.getName().equals(name)) {
                            ParameterTypes parameterTypes = new ParameterTypes(potentialMethod.getParameterTypes());
                            return parameterTypes.isValidMethod(arguments);
                        } else {
                            return false;
                        }
                    }
                });

                if (methods.size() == 0) {
                    throw methodMissingException(name, arguments);
                }

                Method theMethod;
                if (methods.size() == 1) {
                    theMethod = methods.get(0);
                } else {
                    final Class[] argTypes = collectArray(arguments, Class.class, Transformers.type());
                    List<ScoredItem<Method, Long>> scoredByParamDistance = score(methods, new Transformer<Long, Method>() {
                        public Long transform(Method method) {
                            return MetaClassHelper.calculateParameterDistance(argTypes, new ParameterTypes(method.getParameterTypes()));
                        }
                    });

                    Collections.sort(scoredByParamDistance, new Comparator<ScoredItem<Method, Long>>() {
                        public int compare(ScoredItem<Method, Long> o1, ScoredItem<Method, Long> o2) {
                            return o1.getScore().compareTo(o2.getScore());
                        }
                    });

                    theMethod = scoredByParamDistance.get(0).getItem();
                }

                @SuppressWarnings("unchecked") JavaMethod<Object, Object> method = (JavaMethod<Object, Object>) JavaReflectionUtil.method(bean.getClass(), Object.class, theMethod);
                return method.invoke(bean, arguments);
            }
        }

        public boolean isMayImplementMissingMethods() {
            return true;
        }

        public boolean isMayImplementMissingProperties() {
            return true;
        }
    }

    /*
       The GroovyObject interface defines dynamic property and dynamic method methods. Implementers
       are free to implement their own logic in  these methods which makes it invisible to the metaclass.

       The most notable case of this is Closure.

       So in this case we use these methods directly on the GroovyObject in case it does implement logic at this level.
     */
    private class GroovyObjectAdapter extends MetaClassAdapter {
        private final GroovyObject groovyObject = (GroovyObject) bean;


        @Override
        public Object getProperty(String name) throws MissingPropertyException {
            return groovyObject.getProperty(name);
        }

        @Override
        public void setProperty(String name, Object value) throws MissingPropertyException {
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
