/*
 * Copyright 2009 the original author or authors.
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
import org.codehaus.groovy.control.CompilationFailedException;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

public class DefaultClassGenerator implements ClassGenerator {
    // This is static (for now), as these classes are expensive to generate, and also Groovy gets confused
    private static final Map<Class, Class> generatedClasses = new HashMap<Class, Class>();

    public <T> T newInstance(Class<T> type, Object... parameters) {
        return type.cast(ReflectionUtil.newInstance(generate(type), parameters));
    }

    public <T> Class<? extends T> generate(Class<T> type) {
        Class generatedClass = generatedClasses.get(type);
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
        
        if (type.getAnnotation(NoConventionMapping.class) != null) {
            generatedClasses.put(type, type);
            return type;
        }

        String className = type.getSimpleName() + "_WithConventionMapping";

        Formatter src = new Formatter();
        if (type.getPackage() != null) {
            src.format("package %s;%n", type.getPackage().getName());
        }
        src.format("public class %s extends %s implements org.gradle.api.internal.IConventionAware {%n", className,
                type.getName().replaceAll("\\$", "."));
        for (Constructor<?> constructor : type.getConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                src.format("public %s(", className);
                for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                    Class<?> paramType = constructor.getParameterTypes()[i];
                    if (i > 0) {
                        src.format(",");
                    }
                    src.format("%s p%d", paramType.getCanonicalName(), i);
                }
                src.format(") { super(");
                for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                    if (i > 0) {
                        src.format(",");
                    }
                    src.format("p%d", i);
                }
                src.format("); }%n");
            }
        }

        if (!IConventionAware.class.isAssignableFrom(type)) {
            src.format(
                    "private org.gradle.api.internal.ConventionMapping mapping = new org.gradle.api.internal.ConventionAwareHelper(this)%n");
            src.format(
                    "public void setConventionMapping(org.gradle.api.internal.ConventionMapping conventionMapping) { this.mapping = conventionMapping }%n");
            src.format("public org.gradle.api.internal.ConventionMapping getConventionMapping() { return mapping }%n");
        }

        Class noMappingClass = Object.class;
        for (Class<?> c = type.getSuperclass(); c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
            if (c.getAnnotation(NoConventionMapping.class) != null) {
                noMappingClass = c;
            }
        }

        Collection<String> skipProperties = Arrays.asList("metaClass", "conventionMapping");

        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        for (MetaProperty property : (List<MetaProperty>) metaClass.getProperties()) {
            if (skipProperties.contains(property.getName())) {
                continue;
            }
            if (property instanceof MetaBeanProperty) {
                MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;
                MetaMethod getter = metaBeanProperty.getGetter();
                if (getter == null) {
                    continue;
                }
                if (Modifier.isFinal(getter.getModifiers())) {
                    continue;
                }
                Class declaringClass = getter.getDeclaringClass().getTheClass();
                if (declaringClass.isAssignableFrom(noMappingClass)) {
                    continue;
                }
                String returnTypeName = getter.getReturnType().getCanonicalName();
                src.format("public %s %s() { return conventionMapping.getConventionValue(super.%s(), '%s'); }%n",
                        returnTypeName, getter.getName(), getter.getName(), property.getName());
            }
        }

        // todo - make this generic
        if (Task.class.isAssignableFrom(type)) {
            src.format("void setProperty(String name, Object value) { defineProperty(name, value); }%n");
            src.format("def propertyMissing(String name) { property(name); }%n");
        }
        src.format("}");

        GroovyClassLoader classLoader = new GroovyClassLoader(type.getClassLoader());
        try {
            generatedClass = classLoader.parseClass(src.toString());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not generate a proxy class for class %s.", type.getName()),
                    e);
        }
        generatedClasses.put(type, generatedClass);
        return generatedClass;
    }
}
