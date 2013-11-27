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
package org.gradle.internal.typeconversion;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.UncheckedException;
import org.gradle.util.ConfigureUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Converts a {@code Map<String, Object>} to the target type. Subclasses should define a {@code T parseMap()} method which takes a parameter 
 * for each key value required from the source map. Each parameter should be annotated with a {@code @MapKey} annotation, and can also
 * be annotated with a {@code @optional} annotation.
 */
public abstract class MapNotationParser<T> extends TypedNotationParser<Map, T> {
    private final Method convertMethod;
    private final String[] keyNames;
    private final boolean[] optional;

    public MapNotationParser() {
        super(Map.class);
        convertMethod = findConvertMethod();
        keyNames = new String[convertMethod.getParameterAnnotations().length];
        optional = new boolean[convertMethod.getParameterAnnotations().length];
        for (int i = 0; i < convertMethod.getParameterAnnotations().length; i++) {
            Annotation[] annotations = convertMethod.getParameterAnnotations()[i];
            keyNames[i] = keyName(annotations);
            optional[i] = optional(annotations);
        }
    }

    private Method findConvertMethod() {
        for (Method method : getClass().getDeclaredMethods()) {
            if (method.getName().equals("parseMap")) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new UnsupportedOperationException(String.format("No parseMap() method found on class %s.", getClass().getSimpleName()));
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Maps");
    }

    public T parseType(Map values) throws UnsupportedNotationException {
        Map<String, Object> mutableValues = new HashMap<String, Object>(values);
        Set<String> missing = new TreeSet<String>();

        Object[] params = new Object[convertMethod.getParameterTypes().length];
        for (int i = 0; i < params.length; i++) {
            String keyName = keyNames[i];
            boolean optional = this.optional[i];
            Class<?> type = convertMethod.getParameterTypes()[i];
            Object value;
            if (type.equals(String.class)) {
                value = get(mutableValues, keyName);
            } else {
                value = type.cast(mutableValues.get(keyName));
            }
            if (!optional && value == null) {
                missing.add(keyName);
            }
            mutableValues.remove(keyName);
            params[i] = value;
        }
        if (!missing.isEmpty()) {
            //below could be better.
            //Throwing InvalidUserDataException here means that useful context information (including candidate formats, etc.) is not presented to the user
            throw new InvalidUserDataException(String.format("Required keys %s are missing from map %s.", missing, values));
        }

        T result;
        try {
            result = (T) convertMethod.invoke(this, params);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        }

        ConfigureUtil.configureByMap(mutableValues, result);
        return result;
    }

    private boolean optional(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Optional) {
                return true;
            }
        }
        return false;
    }

    private String keyName(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof MapKey) {
                return ((MapKey) annotation).value();
            }
        }
        throw new UnsupportedOperationException("No @Key annotation on parameter of parseMap() method");
    }

    protected String get(Map<String, Object> args, String key) {
        Object value = args.get(key);
        String str = value != null ? value.toString() : null;
        if (str != null && str.length() == 0) {
            return null;
        }
        return str;
    }

}
