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

package org.gradle.model.internal.inspect;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class WeaklyReferencedMethod<T, R> {

    private final ModelType<T> target;
    private final ModelType<R> returnType;
    private final String name;
    private final int modifiers;
    private final ImmutableList<ModelType<?>> paramTypes;

    public WeaklyReferencedMethod(ModelType<T> target, ModelType<R> returnType, Method method) {
        this.target = target;
        this.returnType = returnType;
        this.name = method.getName();
        paramTypes = ImmutableList.copyOf(Iterables.transform(Arrays.asList(method.getParameterTypes()), new Function<Class<?>, ModelType<?>>() {
            public ModelType<?> apply(Class<?> clazz) {
                return ModelType.of(clazz);
            }
        }));
        modifiers = method.getModifiers();

    }

    public int getModifiers() {
        return modifiers;
    }
    public R invoke(T target, Object... args) {
        Method method = findMethod();
        method.setAccessible(true);
        try {
            Object result = method.invoke(target, args);
            return returnType.getConcreteClass().cast(result);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), target), e);
        }
    }

    private Method findMethod() {
        ModelType<Class<?>> classType = new ModelType<Class<?>>() {
        };
        Class<?>[] paramTypesArray = Iterables.toArray(Iterables.transform(paramTypes, new Function<ModelType<?>, Class<?>>() {
            public Class<?> apply(ModelType<?> modelType) {
                return modelType.getRawClass();
            }
        }), classType.getConcreteClass());

        return findMethod(target.getRawClass(), paramTypesArray);
    }

    private Method findMethod(Class<?> currentTarget, Class<?>[] paramTypes) {
        for (Method method : currentTarget.getDeclaredMethods()) {
            if (method.getName().equals(name) && Arrays.equals(paramTypes, method.getParameterTypes())) {
                return method;
            }
        }

        Class<?> parent = currentTarget.getSuperclass();
        if (parent == null) {
            throw new org.gradle.internal.reflect.NoSuchMethodException(String.format("Could not find method %s(%s) on %s.", name, Joiner.on(", ").join(paramTypes), target.getRawClass().getSimpleName()));
        } else {
            return findMethod(parent, paramTypes);
        }
    }
}
