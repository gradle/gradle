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
import org.gradle.api.GradleException;
import org.gradle.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Class, Map<Class, Class>> GENERATED_CLASSES = new HashMap<Class, Map<Class, Class>>();

    public <T> T newInstance(Class<T> type, Object... parameters) {
        return type.cast(ReflectionUtil.newInstance(generate(type), parameters));
    }

    public <T> Class<? extends T> generate(Class<T> type) {
        Map<Class, Class> cache = GENERATED_CLASSES.get(getClass());
        if (cache == null) {
            cache = new HashMap<Class, Class>();
            GENERATED_CLASSES.put(getClass(), cache);
        }
        Class generatedClass = cache.get(type);
        if (generatedClass != null) {
            return generatedClass;
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
            boolean isDynamicAware = type.getAnnotation(NoDynamicObject.class) == null;

            builder.startClass(isConventionAware, isDynamicAware);

            if (isDynamicAware && !DynamicObjectAware.class.isAssignableFrom(type)) {
                builder.mixInDynamicAware();
            }
            if (isDynamicAware && !GroovyObject.class.isAssignableFrom(type)) {
                builder.mixInGroovyObject();
            }
            if (isDynamicAware) {
                builder.addDynamicMethods();
            }
            if (isConventionAware && !IConventionAware.class.isAssignableFrom(type)) {
                builder.mixInConventionAware();
            }

            Class noMappingClass = Object.class;
            for (Class<?> c = type; c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
                if (c.getAnnotation(NoConventionMapping.class) != null) {
                    noMappingClass = c;
                }
            }

            Collection<String> skipProperties = Arrays.asList("metaClass", "conventionMapping", "convention", "asDynamicObject");

            MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
            for (MetaProperty property : metaClass.getProperties()) {
                if (skipProperties.contains(property.getName())) {
                    continue;
                }
                if (property instanceof MetaBeanProperty) {
                    MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;
                    MetaMethod getter = metaBeanProperty.getGetter();
                    if (getter == null) {
                        continue;
                    }
                    if (Modifier.isFinal(getter.getModifiers()) || Modifier.isPrivate(getter.getModifiers())) {
                        continue;
                    }
                    if (getter.getReturnType().isPrimitive()) {
                        continue;
                    }
                    Class declaringClass = getter.getDeclaringClass().getTheClass();
                    if (declaringClass.isAssignableFrom(noMappingClass)) {
                        continue;
                    }
                    builder.addGetter(metaBeanProperty);

                    MetaMethod setter = metaBeanProperty.getSetter();
                    if (setter == null) {
                        continue;
                    }
                    if (Modifier.isFinal(setter.getModifiers()) || Modifier.isPrivate(setter.getModifiers())) {
                        continue;
                    }

                    builder.addSetter(metaBeanProperty);
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
        return subclass;
    }

    protected abstract <T> ClassBuilder<T> start(Class<T> type);

    protected interface ClassBuilder<T> {
        void startClass(boolean isConventionAware, boolean isDynamicAware);

        void addConstructor(Constructor<?> constructor) throws Exception;

        void mixInDynamicAware() throws Exception;

        void mixInConventionAware() throws Exception;

        void mixInGroovyObject() throws Exception;

        void addDynamicMethods() throws Exception;

        void addGetter(MetaBeanProperty property) throws Exception;

        void addSetter(MetaBeanProperty property) throws Exception;

        Class<? extends T> generate() throws Exception;
    }
}
