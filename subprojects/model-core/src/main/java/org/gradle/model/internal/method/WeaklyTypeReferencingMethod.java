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
import java.util.List;

public class WeaklyTypeReferencingMethod<T, R> {

    private final ModelType<T> declaringType;
    private final ModelType<R> returnType;
    private final String name;
    private final ImmutableList<ModelType<?>> paramTypes;
    private final int modifiers;

    private int cachedHashCode = -1;

    private WeaklyTypeReferencingMethod(ModelType<T> declaringType, ModelType<R> returnType, Method method) {
        if (declaringType.getRawClass() != method.getDeclaringClass()) {
            throw new IllegalArgumentException("Unexpected target class.");
        }
        this.declaringType = declaringType;
        this.returnType = returnType;
        this.name = method.getName();
        paramTypes = ImmutableList.copyOf(Iterables.transform(Arrays.asList(method.getGenericParameterTypes()), new Function<Type, ModelType<?>>() {
            public ModelType<?> apply(Type type) {
                return ModelType.of(type);
            }
        }));
        modifiers = method.getModifiers();
    }

    public static WeaklyTypeReferencingMethod<?, ?> of(Method method) {
        return of(ModelType.declaringType(method), ModelType.returnType(method), method);
    }

    public static <T, R> WeaklyTypeReferencingMethod<T, R> of(ModelType<T> target, ModelType<R> returnType, Method method) {
        return new WeaklyTypeReferencingMethod<T, R>(target, returnType, method);
    }

    public ModelType<T> getDeclaringType() {
        return declaringType;
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

    public Annotation[] getAnnotations() {
        //we could retrieve annotations at construction time and hold references to them but unfortunately
        //in IBM JDK strong references are held from annotation instance to class in which it is used so we have to reflect
        return getMethod().getAnnotations();
    }

    public List<ModelType<?>> getGenericParameterTypes() {
        return paramTypes;
    }

    public R invoke(T target, Object... args) {
        Method method = getMethod();
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

    public Method getMethod() {
        Class<?>[] paramTypesArray = Iterables.toArray(Iterables.transform(paramTypes, new Function<ModelType<?>, Class<?>>() {
            public Class<?> apply(ModelType<?> modelType) {
                return modelType.getRawClass();
            }
        }), Class.class);
        try {
            return declaringType.getRawClass().getDeclaredMethod(name, paramTypesArray);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public int hashCode() {
        if (cachedHashCode != -1) {
            return cachedHashCode;
        }
        // there's a risk, for some methods, that the hash is always
        // recomputed but it won't be worse than before
        cachedHashCode = new HashCodeBuilder()
                .append(declaringType)
                .append(returnType)
                .append(name)
                .append(paramTypes)
                .toHashCode();
        return cachedHashCode;
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
                .append(declaringType, other.declaringType)
                .append(returnType, other.returnType)
                .append(name, other.name)
                .append(paramTypes, other.paramTypes)
                .isEquals();
    }

    @Override
    public String toString() {
        return String.format("%s.%s(%s)",
            declaringType.getDisplayName(),
            name,
            Joiner.on(", ").join(Iterables.transform(paramTypes, new Function<ModelType<?>, String>() {
                @Override
                public String apply(ModelType<?> paramType) {
                    return paramType.getDisplayName();
                }
            }))
        );
    }
}
