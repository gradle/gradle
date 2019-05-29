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
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.CachedInvokable;
import org.gradle.internal.reflect.ReflectionCache;
import org.gradle.util.ConfigureUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts a {@code Map<String, Object>} to the target type. Subclasses should define a {@code T parseMap()} method which takes a parameter
 * for each key value required from the source map. Each parameter should be annotated with a {@code @MapKey} annotation, and can also
 * be annotated with a {@code @optional} annotation.
 */
public abstract class MapNotationConverter<T> extends TypedNotationConverter<Map, T> {
    public MapNotationConverter() {
        super(Map.class);
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("Maps");
    }

    @Override
    public T parseType(Map values) throws UnsupportedNotationException {
        Map<String, Object> mutableValues = new HashMap<String, Object>(values);
        Set<String> missing = null;
        ConvertMethod convertMethod = null;
        Method method = null;
        while (method == null) {
            convertMethod = ConvertMethod.of(this.getClass());
            // since we need access to the method and that it's weakly referenced
            // we always need to double check that it hasn't been collected
            method = convertMethod.getMethod();
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] params = new Object[parameterTypes.length];
        String[] keyNames = convertMethod.keyNames;
        boolean[] optionals = convertMethod.optional;
        for (int i = 0; i < params.length; i++) {
            String keyName = keyNames[i];
            boolean optional = optionals[i];
            Class<?> type = parameterTypes[i];
            Object value;
            if (type == String.class) {
                value = get(mutableValues, keyName);
            } else {
                value = type.cast(mutableValues.get(keyName));
            }
            if (!optional && value == null) {
                if (missing == null) {
                    missing = new TreeSet<String>();
                }
                missing.add(keyName);
            }
            mutableValues.remove(keyName);
            params[i] = value;
        }
        if (missing != null) {
            //below could be better.
            //Throwing InvalidUserDataException here means that useful context information (including candidate formats, etc.) is not presented to the user
            throw new InvalidUserDataException(String.format("Required keys %s are missing from map %s.", missing, values));
        }

        T result;
        try {
            result = (T) method.invoke(this, params);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        }

        ConfigureUtil.configureByMap(mutableValues, result);
        return result;
    }

    protected String get(Map<String, Object> args, String key) {
        Object value = args.get(key);
        String str = value != null ? value.toString() : null;
        if (str != null && str.length() == 0) {
            return null;
        }
        return str;
    }

    private static class ConvertMethodCache extends ReflectionCache<ConvertMethod> {

        @Override
        protected ConvertMethod create(Class<?> key, Class<?>[] params) {
            Method convertMethod = findConvertMethod(key);
            Annotation[][] parameterAnnotations = convertMethod.getParameterAnnotations();
            String[] keyNames = new String[parameterAnnotations.length];
            boolean[] optional = new boolean[parameterAnnotations.length];
            for (int i = 0; i < parameterAnnotations.length; i++) {
                Annotation[] annotations = parameterAnnotations[i];
                keyNames[i] = keyName(annotations);
                optional[i] = optional(annotations);
            }
            return new ConvertMethod(convertMethod, keyNames, optional);
        }

        private static Method findConvertMethod(Class clazz) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals("parseMap")) {
                    method.setAccessible(true);
                    return method;
                }
            }
            throw new UnsupportedOperationException(String.format("No parseMap() method found on class %s.", clazz.getSimpleName()));
        }

        private static boolean optional(Annotation[] annotations) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof Optional) {
                    return true;
                }
            }
            return false;
        }

        private static String keyName(Annotation[] annotations) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof MapKey) {
                    return ((MapKey) annotation).value();
                }
            }
            throw new UnsupportedOperationException("No @Key annotation on parameter of parseMap() method");
        }

    }

    private static class ConvertMethod extends CachedInvokable<Method> {
        private final static ConvertMethodCache CONVERT_METHODS = new ConvertMethodCache();
        public static final Class[] EMPTY = new Class[0];

        private final String[] keyNames;
        private final boolean[] optional;

        private ConvertMethod(Method method, String[] keyNames, boolean[] optional) {
            super(method);
            this.keyNames = keyNames;
            this.optional = optional;
        }

        public static synchronized ConvertMethod of(Class clazz) {
            return CONVERT_METHODS.get(clazz, EMPTY);
        }
    }


}
