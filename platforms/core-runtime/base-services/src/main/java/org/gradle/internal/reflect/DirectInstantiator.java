/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.reflect;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.reflect.ObjectInstantiationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * You should use the methods of {@link org.gradle.api.internal.InstantiatorFactory} instead of this type, as it will give better error reporting, more consistent behaviour and better performance.
 * This type will be retired in favor of {@link org.gradle.api.internal.InstantiatorFactory} at some point. Currently it is too tangled with a few things to just remove.
 */
public class DirectInstantiator implements Instantiator {

    /**
     * Please use {@link org.gradle.api.internal.InstantiatorFactory} instead.
     */
    public static final Instantiator INSTANCE = new DirectInstantiator();

    private final ConstructorCache constructorCache = new ConstructorCache();

    /**
     * Please use {@link org.gradle.api.internal.InstantiatorFactory} instead.
     */
    public static <T> T instantiate(Class<? extends T> type, Object... params) {
        return INSTANCE.newInstance(type, params);
    }

    private DirectInstantiator() {
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... params) {
        try {
            Class<?>[] argTypes = wrapArgs(params);
            Constructor<?> match = null;
            while (match == null) {
                // we need to wrap this into a loop, because there's always a risk
                // that the method, which is weakly referenced, has been collected
                // in between the creation time and now
                match = constructorCache.get(type, argTypes).getMethod();
            }
            return type.cast(match.newInstance(params));
        } catch (InvocationTargetException e) {
            throw new ObjectInstantiationException(type, e.getCause());
        } catch (Throwable t) {
            throw new ObjectInstantiationException(type, t);
        }
    }

    private Class<?>[] wrapArgs(Object[] params) {
        Class<?>[] result = new Class<?>[params.length];
        for (int i = 0; i < result.length; i++) {
            Object param = params[i];
            if (param == null) {
                continue;
            }
            Class<?> pType = param.getClass();
            if (pType.isPrimitive()) {
                pType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(pType);
            }
            result[i] = pType;
        }
        return result;
    }

    @VisibleForTesting
    public static class ConstructorCache extends ReflectionCache<CachedConstructor> {

        @Override
        protected CachedConstructor create(Class<?> receiver, Class<?>[] argumentTypes) {
            Constructor<?>[] constructors = receiver.getConstructors();
            Constructor<?> match = null;
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == argumentTypes.length) {
                    if (isMatch(argumentTypes, parameterTypes)) {
                        if (match != null) {
                            throw new IllegalArgumentException(String.format("Found multiple public constructors for %s which accept parameters [%s].", receiver, prettify(argumentTypes)));
                        }
                        match = constructor;
                    }
                }
            }
            if (match == null) {
                throw new IllegalArgumentException(String.format("Could not find any public constructor for %s which accepts parameters [%s].", receiver, prettify(argumentTypes)));
            }
            return new CachedConstructor(match);
        }

        private String prettify(Class<?>[] argumentTypes) {
            return Joiner.on(", ").join(Iterables.transform(Arrays.asList(argumentTypes), new Function<Class<?>, String>() {
                @Override
                public String apply(Class<?> input) {
                    if (input == null) {
                        return "null";
                    }
                    return input.getName();
                }
            }));
        }

        private boolean isMatch(Class<?>[] argumentTypes, Class<?>[] parameterTypes) {
            for (int i = 0; i < argumentTypes.length; i++) {
                Class<?> argumentType = argumentTypes[i];
                Class<?> parameterType = parameterTypes[i];
                boolean primitive = parameterType.isPrimitive();
                if (primitive) {
                    if (argumentType == null) {
                        return false;
                    }
                    parameterType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(parameterType);
                }
                if (argumentType != null && !parameterType.isAssignableFrom(argumentType)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class CachedConstructor extends CachedInvokable<Constructor<?>> {
        public CachedConstructor(Constructor<?> ctor) {
            super(ctor);
        }
    }

}
