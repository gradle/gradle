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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultClassGenerator implements ClassGenerator {
    private final Map<Class, Class> generatedClasses = new HashMap<Class, Class>();

    public <T> Class<? extends T> generate(Class<T> type) {
        Class generatedClass = generatedClasses.get(type);
        if (generatedClass != null) {
            return generatedClass;
        }

        String className = type.getSimpleName() + "_WithConventionMapping";

        Formatter src = new Formatter();
        if (type.getPackage() != null) {
            src.format("package %s;%n", type.getPackage().getName());
        }
        src.format("public class %s extends %s {%n", className, type.getName().replaceAll("\\$", "."));
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
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        List<MetaProperty> properties = metaClass.getProperties();
        for (MetaProperty property : properties) {
            if (property.getName().equals("metaClass")) {
                continue;
            }
            if (property instanceof MetaBeanProperty) {
                MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;
                MetaMethod getter = metaBeanProperty.getGetter();
                if (getter != null && !Modifier.isFinal(getter.getModifiers()) && ConventionTask.class.isAssignableFrom(getter.getDeclaringClass().getTheClass())) {
                    String returnTypeName = getter.getReturnType().getCanonicalName();
                    src.format("public %s %s() { return conv(super.%s(), '%s'); }%n", returnTypeName,
                            getter.getName(), getter.getName(), property.getName());
                }
            }
        }
        src.format("void setProperty(String name, Object value) { defineProperty(name, value); }%n");
        src.format("def propertyMissing(String name) { property(name); }%n");
        src.format("}");

        GroovyClassLoader classLoader = new GroovyClassLoader(type.getClassLoader());
        try {
            generatedClass = classLoader.parseClass(src.toString());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not generate a proxy class for class %s.",
                    type.getName()), e);
        }
        generatedClasses.put(type, generatedClass);
        return generatedClass;
    }
}
