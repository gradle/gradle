/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.scala.internal.reflect;

import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ScalaMethod {
    private final String description;
    private final Method method;
    private final Object instance;

    public ScalaMethod(ClassLoader classLoader, String className, String methodName, Class<?>... typeParameters) {
        description = String.format("%s.%s()", className, methodName);
        Class<?> baseClass = getClass(classLoader, className);
        final Field scalaObject = getModule(baseClass);
        instance = getInstance(scalaObject);
        method = getMethod(scalaObject.getType(), methodName, typeParameters);
    }

    private Method getMethod(Class<?> type, String methodName, Class<?>[] typeParameters) {
        try {
            return type.getMethod(methodName, typeParameters);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Object getInstance(Field scalaObject) {
        try {
            return scalaObject.get(null);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Class<?> getClass(ClassLoader classLoader, String typeName) {
        try {
            return classLoader.loadClass(typeName + "$");
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Field getModule(Class<?> baseClass) {
        try {
            return baseClass.getField("MODULE$");
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public Object invoke(Object... args) {
        try {
            return method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not invoke Scala method %s.", description), e);
        }
    }

}
