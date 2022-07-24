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

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.transform;
import static java.util.Arrays.asList;

/**
 * Provides a way to preserve high-fidelity {@link Type} information on generic types.
 *
 * Capture a generic type with an anonymous subclass. For example: <pre>   {@code
 *   new TypeOf<NamedDomainObjectContainer<ArtifactRepository>>() {}}</pre>
 *
 * @param <T> Parameterized type
 * @since 3.5
 */
public abstract class TypeOf<T> {

    /**
     * Creates an instance of {@literal TypeOf} for the given {@literal Class}.
     *
     * @param type the {@literal Class}
     * @param <T> the parameterized type of the given {@literal Class}
     * @return the {@literal TypeOf} that captures the generic type of the given {@literal Class}
     */
    public static <T> TypeOf<T> typeOf(Class<T> type) {
        return new TypeOf<T>(
            ModelType.of(typeWhichCannotBeNull(type))) {
        };
    }

    /**
     * Creates an instance of {@literal TypeOf} for the given {@literal Type}.
     *
     * @param type the {@literal Type}
     * @param <T> the parameterized type of the given {@literal Type}
     * @return the {@literal TypeOf} that captures the generic type of the given {@literal Type}
     */
    public static <T> TypeOf<T> typeOf(Type type) {
        return new TypeOf<T>(
            Cast.<ModelType<T>>uncheckedCast(
                ModelType.of(typeWhichCannotBeNull(type)))) {
        };
    }

    /**
     * Constructs a new parameterized type from a given parameterized type definition and an array of type arguments.
     *
     * For example, {@code parameterizedTypeOf(new TypeOf<List<?>>() {}, new TypeOf<String>() {})} is equivalent to
     * {@code new TypeOf<List<String>>() {}}, except both the parameterized type definition and type arguments can be dynamically computed.
     *
     * @param parameterizedType the parameterized type from which to construct the new parameterized type
     * @param typeArguments the arguments with which to construct the new parameterized type
     * @see #isParameterized()
     */
    public static TypeOf<?> parameterizedTypeOf(TypeOf<?> parameterizedType, TypeOf<?>... typeArguments) {
        ModelType<?> parameterizedModelType = parameterizedType.type;
        if (!parameterizedModelType.isParameterized()) {
            throw new IllegalArgumentException("Expecting a parameterized type, got: " + parameterizedType + ".");
        }
        return typeOf(parameterizedModelType.withArguments(modelTypeListFrom(typeArguments)));
    }

    private final ModelType<T> type;

    private TypeOf(ModelType<T> type) {
        this.type = type;
    }

    protected TypeOf() {
        this.type = captureTypeArgument();
    }

    /**
     * Queries whether this object represents a simple (non-composite) type, not an array and not a generic type.
     *
     * @return true if this object represents a simple type.
     */
    public boolean isSimple() {
        return type.isClass()
            && !rawClass().isArray();
    }

    /**
     * Queries whether this object represents a synthetic type as defined by {@link Class#isSynthetic()}.
     *
     * @return true if this object represents a synthetic type.
     */
    public boolean isSynthetic() {
        return rawClass().isSynthetic();
    }

    /**
     * Queries whether the type represented by this object is public ({@link java.lang.reflect.Modifier#isPublic(int)}).
     *
     * @see java.lang.reflect.Modifier#isPublic(int)
     * @see Class#getModifiers()
     */
    public boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }

    private int getModifiers() {
        return rawClass().getModifiers();
    }

    /**
     * Queries whether this object represents an array, generic or otherwise.
     *
     * @return true if this object represents an array.
     * @see #getComponentType()
     */
    public boolean isArray() {
        return type.isGenericArray()
            || (type.isClass() && rawClass().isArray());
    }

    /**
     * Returns the component type of the array type this object represents.
     *
     * @return null if this object does not represent an array type.
     * @see #isArray()
     */
    @Nullable
    public TypeOf<?> getComponentType() {
        return type.isGenericArray()
            ? typeOf(type.getComponentType())
            : nullableTypeOf(rawClass().getComponentType());
    }

    /**
     * Queries whether this object represents a parameterized type.
     *
     * @return true if this object represents a parameterized type.
     * @see #getParameterizedTypeDefinition()
     * @see #getActualTypeArguments()
     */
    public boolean isParameterized() {
        return type.isParameterized();
    }

    /**
     * Returns an object that represents the type from which this parameterized type was constructed.
     *
     * @see #isParameterized()
     */
    public TypeOf<?> getParameterizedTypeDefinition() {
        return typeOf(type.getRawType());
    }

    /**
     * Returns the list of type arguments used in the construction of this parameterized type.
     *
     * @see #isParameterized()
     */
    public List<TypeOf<?>> getActualTypeArguments() {
        return typeOfListFrom(type.getTypeVariables());
    }

    /**
     * Queries whether this object represents a wildcard type expression, such as
     * {@code ?}, {@code ? extends Number}, or {@code ? super Integer}.
     *
     * @return true if this object represents a wildcard type expression.
     * @see #getUpperBound()
     */
    public boolean isWildcard() {
        return type.isWildcard();
    }

    /**
     * Returns the first declared upper-bound of the wildcard type expression represented by this type.
     *
     * @return null if no upper-bound has been explicitly declared.
     */
    @Nullable
    public TypeOf<?> getUpperBound() {
        return nullableTypeOf(type.getUpperBound());
    }

    /**
     * Returns the first declared lower-bound of the wildcard type expression represented by this type.
     *
     * @return null if no lower-bound has been explicitly declared.
     * @since 6.0
     */
    @Nullable
    public TypeOf<?> getLowerBound() {
        return nullableTypeOf(type.getLowerBound());
    }

    /**
     * Is this type assignable from the given type?
     *
     * @param type the given type
     * @return {@literal true} if this type is assignable from the given type, {@literal false otherwise}
     */
    public final boolean isAssignableFrom(TypeOf<?> type) {
        return this.type.isAssignableFrom(type.type);
    }

    /**
     * Is this type assignable from the given type?
     *
     * @param type the given type
     * @return {@literal true} if this type is assignable from the given type, {@literal false otherwise}
     */
    public final boolean isAssignableFrom(Type type) {
        return this.type.isAssignableFrom(ModelType.of(type));
    }

    /**
     * Simple name.
     *
     * @return this type's simple name
     */
    public String getSimpleName() {
        return type.getDisplayName();
    }

    /**
     * Fully Qualified name.
     *
     * @return this type's FQN
     * @since 7.4
     */
    @Incubating
    public String getFullyQualifiedName() {
        return type.getName();
    }

    /**
     * <p>
     * This returns the underlying, concrete Java {@link java.lang.Class}.
     * </p>
     * <p>
     * For example, a simple {@code TypeOf<String>} will be the given generic type {@code String.class}.
     * <br>
     * Generic types like {@code TypeOf<List<String>>} would have the concrete type of {@code List.class}.
     * <br>
     * For array types like {@code TypeOf<String[]>}, the concrete type will be an array of the component type ({@code String[].class}).
     * </p>
     *
     * @since 5.0
     *
     * @return Underlying Java Class of this type.
     */
    public Class<T> getConcreteClass() {
        return type.getConcreteClass();
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

    private Class<? super T> rawClass() {
        return type.getRawClass();
    }

    private static <T> T typeWhichCannotBeNull(T type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null.");
        }
        return type;
    }

    private static List<ModelType<?>> modelTypeListFrom(TypeOf<?>[] typeOfs) {
        return map(asList(typeOfs), new Function<TypeOf<?>, ModelType<?>>() {
            @Override
            public ModelType<?> apply(TypeOf<?> it) {
                return it.type;
            }
        });
    }

    private static List<TypeOf<?>> typeOfListFrom(List<ModelType<?>> modelTypes) {
        return map(modelTypes, new Function<ModelType<?>, TypeOf<?>>() {
            @Override
            public TypeOf<?> apply(ModelType<?> it) {
                return typeOf(it);
            }
        });
    }

    private static <U> TypeOf<U> typeOf(ModelType<U> componentType) {
        return new TypeOf<U>(componentType) {
        };
    }

    private TypeOf<?> nullableTypeOf(Class<?> type) {
        return type != null
            ? typeOf(type)
            : null;
    }

    private TypeOf<?> nullableTypeOf(ModelType<?> type) {
        return type != null
            ? typeOf(type)
            : null;
    }

    private static <T, U> List<U> map(Iterable<T> iterable, Function<T, U> function) {
        return copyOf(transform(iterable, function));
    }
}
