/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.reflect;

import com.google.common.base.Objects;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Incubating;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Provides a way to preserve high-fidelity {@link Type} information on generic types.
 *
 * Capture a generic type with an anonymous subclass. For example: <pre>   {@code
 *   new TypeOf<NamedDomainObjectContainer<ArtifactRepository>>() {}}</pre>
 *
 * @param <T> Parameterized type
 * @since 3.5
 */
@Incubating
public abstract class TypeOf<T> {

    public static <T> TypeOf<T> of(Class<T> type) {
        return new TypeOf<T>(TypeToken.of(type)) {};
    }

    public static <T> TypeOf<T> of(Type type) {
        return new TypeOf<T>(Cast.<TypeToken<T>>uncheckedCast(TypeToken.of(type))) {};
    }

    private final TypeToken<T> token;

    private TypeOf(TypeToken<T> token) {
        this.token = token;
    }

    protected TypeOf() {
        this.token = captureTypeArgument();
    }

    private TypeToken<T> captureTypeArgument() {
        Type genericSuperclass = getClass().getGenericSuperclass();
        Type type = genericSuperclass instanceof ParameterizedType
            ? ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0]
            : Object.class;
        return Cast.uncheckedCast(TypeToken.of(type));
    }

    public final Type getType() {
        return token.getType();
    }

    public final Class<? super T> getRawType() {
        return token.getRawType();
    }

    public final List<Type> getTypeArguments() {
        if (token.isArray()) {
            return singletonList(token.getComponentType().getType());
        }
        if (token.getType() instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) token.getType();
            return asList(pt.getActualTypeArguments());
        }
        return emptyList();
    }

    public final List<TypeOf<?>> getTypeOfArguments() {
        List<Type> typeArguments = getTypeArguments();
        List<TypeOf<?>> typeOfArguments = new ArrayList<TypeOf<?>>(typeArguments.size());
        for (Type typeArgument : typeArguments) {
            typeOfArguments.add(of(typeArgument));
        }
        return typeOfArguments;
    }

    public final List<Class<?>> getRawTypeArguments() {
        List<TypeOf<?>> typeOfArguments = getTypeOfArguments();
        List<Class<?>> rawTypeArguments = new ArrayList<Class<?>>(typeOfArguments.size());
        for (TypeOf<?> typeOfArgument : typeOfArguments) {
            rawTypeArguments.add(typeOfArgument.getRawType());
        }
        return rawTypeArguments;
    }

    public final boolean isAssignableFrom(TypeOf<?> type) {
        return isAssignableFrom(type.getType());
    }

    public final boolean isAssignableFrom(Type type) {
        return token.isAssignableFrom(type);
    }

    public String getSimpleName() {
        return Types.getGenericSimpleName(token.getType());
    }

    @Override
    public final String toString() {
        return token.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof TypeOf)) {
            return false;
        }
        TypeOf<?> typeOf = (TypeOf<?>) o;
        return Objects.equal(token, typeOf.token);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(token);
    }
}
