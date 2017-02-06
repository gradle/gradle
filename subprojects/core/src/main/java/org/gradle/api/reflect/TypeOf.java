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

import com.google.common.base.Function;
import org.gradle.api.Incubating;
import org.gradle.internal.Cast;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.transform;

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

    public static <T> TypeOf<T> typeOf(Class<T> type) {
        return new TypeOf<T>(ModelType.of(type)) {};
    }

    public static <T> TypeOf<T> typeOf(Type type) {
        return new TypeOf<T>(Cast.<ModelType<T>>uncheckedCast(ModelType.of(type))) {};
    }

    /**
     * Provides a mechanism for pattern-matching on the structure of a type.
     */
    public interface Visitor {
        void visitArrayOf(TypeOf<?> componentType);
        void visitParameterized(TypeOf<?> rawType, List<TypeOf<?>> typeArguments);
        void visitSimple(TypeOf<?> type);
    }

    private final ModelType<T> type;

    private TypeOf(ModelType<T> type) {
        this.type = type;
    }

    protected TypeOf() {
        this.type = captureTypeArgument();
    }

    public final boolean isAssignableFrom(TypeOf<?> type) {
        return this.type.isAssignableFrom(type.type);
    }

    public final boolean isAssignableFrom(Type type) {
        return this.type.isAssignableFrom(ModelType.of(type));
    }

    public String getSimpleName() {
        return type.getDisplayName();
    }

    public void accept(Visitor visitor) {
        if (type.isClass()) {
            Class<? super T> rawClass = type.getRawClass();
            if (rawClass.isArray()) {
                visitor.visitArrayOf(typeOf(rawClass.getComponentType()));
            } else {
                visitor.visitSimple(typeOf(type));
            }
            return;
        }
        if (type.isParameterized()) {
            visitor.visitParameterized(typeOf(type.getRawClass()), listOf(type.getTypeVariables()));
            return;
        }
        if (type.isGenericArray()) {
            visitor.visitArrayOf(typeOf(type.getComponentType()));
            return;
        }
        throw new IllegalStateException("Cannot accept visitor for " + type + ".");
    }

    @Override
    public final String toString() {
        return type.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeOf)) {
            return false;
        }
        TypeOf<?> typeOf = (TypeOf<?>) o;
        return type.equals(typeOf.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    private ModelType<T> captureTypeArgument() {
        Type genericSuperclass = getClass().getGenericSuperclass();
        Type type = genericSuperclass instanceof ParameterizedType
            ? ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0]
            : Object.class;
        return Cast.uncheckedCast(ModelType.of(type));
    }

    private List<TypeOf<?>> listOf(List<ModelType<?>> types) {
        return copyOf(transform(types, new Function<ModelType<?>, TypeOf<?>>() {
            @Override
            public TypeOf<?> apply(ModelType<?> input) {
                return typeOf(input);
            }
        }));
    }

    private <T> TypeOf<T> typeOf(ModelType<T> componentType) {
        return new TypeOf<T>(componentType) {};
    }
}
