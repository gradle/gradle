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

package org.gradle.model.internal.method;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.GradleException;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

public class WeaklyTypeReferencingMethod<T, R> {

    private final ModelType<T> target;
    private final ModelType<R> returnType;
    private final ModelType<?> declaringType;
    private final String name;
    private final ImmutableList<ModelType<?>> paramTypes;
    private final int modifiers;


    public WeaklyTypeReferencingMethod(ModelType<T> target, ModelType<R> returnType, Method method) {
        this.target = target;
        this.returnType = returnType;
        this.declaringType = ModelType.of(method.getDeclaringClass());
        this.name = method.getName();
        paramTypes = ImmutableList.copyOf(Iterables.transform(Arrays.asList(method.getGenericParameterTypes()), new Function<Type, ModelType<?>>() {
            public ModelType<?> apply(Type type) {
                return ModelType.of(type);
            }
        }));
        modifiers = method.getModifiers();
    }

    public static <T, R> WeaklyTypeReferencingMethod<T, R> of(ModelType<T> target, ModelType<R> returnType, Method method) {
        return new WeaklyTypeReferencingMethod<T, R>(target, returnType, method);
    }

    public ModelType<T> getTarget() {
        return target;
    }

    public ModelType<R> getReturnType() {
        return returnType;
    }

    public String getName() {
        return name;
    }

    public int getModifiers() {
        return modifiers;
    }

    public Class<?> getDeclaringClass() {
        return declaringType.getRawClass();
    }

    public Annotation[] getAnnotations() {
        //we could retrieve annotations at construction time and hold references to them but unfortunately
        //in IBM JDK strong references are held from annotation instance to class in which it is used so we have to reflect
        return findMethod().getAnnotations();
    }

    public Type[] getGenericParameterTypes() {
        return Iterables.toArray(Iterables.transform(paramTypes, new Function<ModelType<?>, Type>() {
            public Type apply(ModelType<?> modelType) {
                return modelType.getType();
            }
        }), Type.class);
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

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(target)
                .append(returnType)
                .append(name)
                .append(paramTypes)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WeaklyTypeReferencingMethod)) {
            return false;
        }

        WeaklyTypeReferencingMethod<?, ?> other = Cast.uncheckedCast(obj);

        return new EqualsBuilder()
                .append(target, other.target)
                .append(returnType, other.returnType)
                .append(name, other.name)
                .append(paramTypes, other.paramTypes)
                .isEquals();
    }
}
