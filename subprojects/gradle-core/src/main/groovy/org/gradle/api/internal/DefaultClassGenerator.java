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
import org.gradle.api.internal.tasks.DynamicObjectAware;
import org.gradle.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

public class DefaultClassGenerator implements ClassGenerator {
    // This is static (for now), as these classes are expensive to generate, and also Groovy gets confused
    private static final Map<Class, Class> generatedClasses = new WeakHashMap<Class, Class>();

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
        
        String className = type.getSimpleName() + "_Generated";

        boolean addConventionAware = type.getAnnotation(NoConventionMapping.class) == null;
        boolean addDynamicAware = type.getAnnotation(NoDynamicObject.class) == null;

        Formatter src = new Formatter();
        if (type.getPackage() != null) {
            src.format("package %s;%n", type.getPackage().getName());
        }
        src.format("public class %s extends %s ", className, type.getName().replaceAll("\\$", "."));
        if (addConventionAware) {
            src.format("implements org.gradle.api.internal.IConventionAware ");
        }
        if (addDynamicAware) {
            src.format(addConventionAware ? ", " : "implements ");
            src.format("org.gradle.api.internal.tasks.DynamicObjectAware ");
        }
        src.format("{%n");

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

        if (addDynamicAware && !DynamicObjectAware.class.isAssignableFrom(type)) {
            src.format("private org.gradle.api.internal.DynamicObjectHelper dynamicObject = new org.gradle.api.internal.DynamicObjectHelper(this, new org.gradle.api.internal.plugins.DefaultConvention())%n");
            src.format("public void setConvention(org.gradle.api.plugins.Convention convention) { dynamicObject.setConvention(convention); getConventionMapping().setConvention(convention) }%n");
            src.format("public org.gradle.api.plugins.Convention getConvention() { return dynamicObject.getConvention() }%n");
            src.format("public org.gradle.api.internal.DynamicObject getAsDynamicObject() { return dynamicObject }%n");
        }
        if (addDynamicAware) {
            src.format("void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }%n");
            src.format("def propertyMissing(String name) { getAsDynamicObject().getProperty(name); }%n");
            src.format("def methodMissing(String name, Object params) { getAsDynamicObject().invokeMethod(name, (Object[])params); }%n");
        }
        
        if (addConventionAware && !IConventionAware.class.isAssignableFrom(type)) {
            if (addDynamicAware) {
                src.format("private org.gradle.api.internal.ConventionMapping mapping = new org.gradle.api.internal.ConventionAwareHelper(this, getConvention())%n");
            } else {
                src.format("private org.gradle.api.internal.ConventionMapping mapping = new org.gradle.api.internal.ConventionAwareHelper(this, new org.gradle.api.internal.plugins.DefaultConvention())%n");
            }
            src.format("public void setConventionMapping(org.gradle.api.internal.ConventionMapping conventionMapping) { this.mapping = conventionMapping }%n");
            src.format("public org.gradle.api.internal.ConventionMapping getConventionMapping() { return mapping }%n");
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
                if (Modifier.isFinal(getter.getModifiers())) {
                    continue;
                }
                Class declaringClass = getter.getDeclaringClass().getTheClass();
                if (declaringClass.isAssignableFrom(noMappingClass)) {
                    continue;
                }
                String returnTypeName = getter.getReturnType().getCanonicalName();
                src.format("public %s %s() { return getConventionMapping().getConventionValue(super.%s(), '%s'); }%n",
                        returnTypeName, getter.getName(), getter.getName(), property.getName());
            }
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
