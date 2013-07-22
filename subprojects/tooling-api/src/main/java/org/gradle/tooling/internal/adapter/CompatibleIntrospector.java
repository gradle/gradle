/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import org.gradle.internal.UncheckedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Uses reflection to find out / call methods.
 */
public class CompatibleIntrospector {

    private final Object target;

    public CompatibleIntrospector(Object target) {
        this.target = target;
    }

    private Method getMethod(String methodName) throws NoSuchMethodException {
        Method[] methods = target.getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        throw new NoSuchMethodException("No such method: '" + methodName + "' on type: '" + target.getClass().getSimpleName() + "'.");
    }

    public <T> T getSafely(T defaultValue, String methodName) {
        try {
            Method method = getMethod(methodName);
            method.setAccessible(true);
            return (T) method.invoke(target);
        } catch (NoSuchMethodException e) {
            return defaultValue;
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get value reflectively", e);
        }
    }

    public void callSafely(String methodName, Object ... params) {
        Method method;
        try {
            method = getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return; // ignore
        }

        method.setAccessible(true);
        try {
            method.invoke(target, params);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Unable to call method reflectively", e);
        }
    }
}