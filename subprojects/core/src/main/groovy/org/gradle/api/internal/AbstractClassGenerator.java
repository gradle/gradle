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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import groovy.lang.*;
import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.codehaus.groovy.reflection.CachedClass;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Class<?>, Map<Class<?>, Class<?>>> GENERATED_CLASSES = new HashMap<Class<?>, Map<Class<?>, Class<?>>>();
    private static final Lock CACHE_LOCK = new ReentrantLock();

    public <T> T newInstance(Class<T> type, Object... parameters) {
        Instantiator instantiator = new DirectInstantiator();
        return instantiator.newInstance(generate(type), parameters);
    }

    public <T> Class<? extends T> generate(Class<T> type) {
        try {
            CACHE_LOCK.lock();
            return generateUnderLock(type);
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private <T> Class<? extends T> generateUnderLock(Class<T> type) {
        Map<Class<?>, Class<?>> cache = GENERATED_CLASSES.get(getClass());
        if (cache == null) {
            // WeakHashMap won't work here. It keeps a strong reference to the mapping value, which is the generated class in this case
            // However, the generated class has a strong reference to the source class (it extends it), so the keys will always be
            // strongly reachable while this Class is strongly reachable. Use weak references for both key and value of the mapping instead.
            cache = new ReferenceMap(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK);
            GENERATED_CLASSES.put(getClass(), cache);
        }
        Class<?> generatedClass = cache.get(type);
        if (generatedClass != null) {
            return generatedClass.asSubclass(type);
        }

        if (Modifier.isPrivate(type.getModifiers())) {
            throw new GradleException(String.format("Cannot create a proxy class for private class '%s'.",
                    type.getSimpleName()));
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new GradleException(String.format("Cannot create a proxy class for abstract class '%s'.",
                    type.getSimpleName()));
        }

        Class<? extends T> subclass;
        try {
            ClassBuilder<T> builder = start(type);

            boolean isConventionAware = type.getAnnotation(NoConventionMapping.class) == null;

            builder.startClass(isConventionAware);

            if (!DynamicObjectAware.class.isAssignableFrom(type)) {
                if (ExtensionAware.class.isAssignableFrom(type)) {
                    throw new UnsupportedOperationException("A type that implements ExtensionAware must currently also implement DynamicObjectAware.");
                }
                builder.mixInDynamicAware();
            }
            if (!GroovyObject.class.isAssignableFrom(type)) {
                builder.mixInGroovyObject();
            }
            builder.addDynamicMethods();
            if (isConventionAware && !IConventionAware.class.isAssignableFrom(type)) {
                builder.mixInConventionAware();
            }

            Class noMappingClass = Object.class;
            for (Class<?> c = type; c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
                if (c.getAnnotation(NoConventionMapping.class) != null) {
                    noMappingClass = c;
                }
            }

            Collection<String> skipProperties = Arrays.asList("metaClass", "conventionMapping", "convention", "asDynamicObject", "extensions");

            Set<MetaBeanProperty> settableProperties = new HashSet<MetaBeanProperty>();
            Set<MetaBeanProperty> conventionProperties = new HashSet<MetaBeanProperty>();

            MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
            for (MetaProperty property : metaClass.getProperties()) {
                if (skipProperties.contains(property.getName())) {
                    continue;
                }
                if (property instanceof MetaBeanProperty) {
                    MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;

                    boolean needsConventionMapping = true;
                    MetaMethod getter = metaBeanProperty.getGetter();
                    if (getter == null) {
                        needsConventionMapping = false;
                    } else {
                        if (Modifier.isFinal(getter.getModifiers()) || Modifier.isPrivate(getter.getModifiers())) {
                            needsConventionMapping = false;
                        } else {
                            Class declaringClass = getter.getDeclaringClass().getTheClass();
                            if (declaringClass.isAssignableFrom(noMappingClass)) {
                                needsConventionMapping = false;
                            }
                        }
                    }

                    if (needsConventionMapping) {
                        conventionProperties.add(metaBeanProperty);
                        builder.addGetter(metaBeanProperty);
                    }

                    MetaMethod setter = metaBeanProperty.getSetter();
                    if (setter == null || Modifier.isPrivate(setter.getModifiers())) {
                        continue;
                    }

                    if (needsConventionMapping && !Modifier.isFinal(setter.getModifiers())) {
                        builder.addSetter(metaBeanProperty);
                    }

                    if (Iterable.class.isAssignableFrom(property.getType())) {
                        continue;
                    }

                    settableProperties.add(metaBeanProperty);
                }
            }

            Multimap<String, MetaMethod> methods = HashMultimap.create();
            Set<MetaMethod> actionMethods = new HashSet<MetaMethod>();

            for (MetaMethod method : metaClass.getMethods()) {
                if (method.isPrivate()) {
                    continue;
                }
                CachedClass[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 0) {
                    continue;
                }
                methods.put(method.getName(), method);

                CachedClass lastParameter = parameterTypes[parameterTypes.length - 1];
                if (lastParameter.getTheClass().equals(Action.class)) {
                    actionMethods.add(method);
                }
            }

            for (MetaMethod method : actionMethods) {
                boolean hasClosure = false;
                Class[] actionMethodParameterTypes = method.getNativeParameterTypes();
                int numParams = actionMethodParameterTypes.length;
                Class[] closureMethodParameterTypes = new Class[actionMethodParameterTypes.length];
                System.arraycopy(actionMethodParameterTypes, 0, closureMethodParameterTypes, 0, actionMethodParameterTypes.length);
                closureMethodParameterTypes[numParams - 1] = Closure.class;
                for (MetaMethod otherMethod : methods.get(method.getName())) {
                    if (Arrays.equals(otherMethod.getNativeParameterTypes(), closureMethodParameterTypes)) {
                        hasClosure = true;
                        break;
                    }
                }
                if (!hasClosure) {
                    builder.addActionMethod(method);
                }
            }

            // Adds a set method for each mutable property
            for (MetaBeanProperty property : settableProperties) {
                Collection<MetaMethod> methodsForProperty = methods.get(property.getName());
                boolean hasSetMethod = false;
                for (MetaMethod method : methodsForProperty) {
                    if (method.getParameterTypes().length == 1) {
                        if (conventionProperties.contains(property)) {
                            builder.overrideSetMethod(property, method);
                        }
                        hasSetMethod = true;
                    }
                }

                if (!hasSetMethod) {
                    builder.addSetMethod(property);
                }
            }

            for (Constructor<?> constructor : type.getConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())) {
                    builder.addConstructor(constructor);
                }
            }

            subclass = builder.generate();
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not generate a proxy class for class %s.", type.getName()), e);
        }

        cache.put(type, subclass);
        cache.put(subclass, subclass);
        return subclass;
    }

    protected abstract <T> ClassBuilder<T> start(Class<T> type);

    protected interface ClassBuilder<T> {
        void startClass(boolean isConventionAware);

        void addConstructor(Constructor<?> constructor) throws Exception;

        void mixInDynamicAware() throws Exception;

        void mixInConventionAware() throws Exception;

        void mixInGroovyObject() throws Exception;

        void addDynamicMethods() throws Exception;

        void addGetter(MetaBeanProperty property) throws Exception;

        void addSetter(MetaBeanProperty property) throws Exception;

        void overrideSetMethod(MetaBeanProperty property, MetaMethod metaMethod) throws Exception;

        void addSetMethod(MetaBeanProperty property) throws Exception;

        Class<? extends T> generate() throws Exception;

        void addActionMethod(MetaMethod method) throws Exception;
    }
}
