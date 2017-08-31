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

package org.gradle.model.internal.type;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.List;

/**
 * A type token, representing a resolved type.
 * <p>
 * Importantly, instances do not hold strong references to class objects.
 * <p>
 * Construct a type via one of the public static methods, or by creating an AIC…
 * <pre>{@code
 * ModelType<List<String>> type = new ModelType<List<String>>() {};
 * }</pre>
 */
@ThreadSafe
public abstract class ModelType<T> {

    public static final ModelType<Object> UNTYPED = ModelType.of(Object.class);

    private final TypeWrapper wrapper;

    private ModelType(TypeWrapper wrapper) {
        this.wrapper = wrapper;
    }

    protected ModelType() {
        this.wrapper = wrap(new TypeToken<T>(getClass()) {
        }.getType());
    }

    public static <T> ModelType<T> of(Class<T> clazz) {
        return new Simple<T>(clazz);
    }

    public static <T> ModelType<T> returnType(Method method) {
        return new Simple<T>(method.getGenericReturnType());
    }

    public static <T> ModelType<T> declaringType(Method method) {
        return new Simple<T>(method.getDeclaringClass());
    }

    @Nullable
    public static <T> ModelType<T> paramType(Method method, int i) {
        Type[] parameterTypes = method.getGenericParameterTypes();
        if (i < parameterTypes.length) {
            return new Simple<T>(parameterTypes[i]);
        } else {
            return null;
        }
    }

    public static <T> ModelType<T> typeOf(T instance) {
        // TODO: should validate that clazz is of a non parameterized type
        @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) instance.getClass();
        return of(clazz);
    }

    public static ModelType<?> of(Type type) {
        return Simple.typed(type);
    }

    /**
     * Returns true if this type represents a class.
     */
    public boolean isClass() {
        return wrapper instanceof ClassTypeWrapper;
    }

    public Class<? super T> getRawClass() {
        return Cast.uncheckedCast(wrapper.getRawClass());
    }

    public Class<T> getConcreteClass() {
        return Cast.uncheckedCast(wrapper.getRawClass());
    }

    public boolean isRawClassOfParameterizedType() {
        return wrapper instanceof ClassTypeWrapper && ((ClassTypeWrapper) wrapper).unwrap().getTypeParameters().length > 0;
    }

    public static ModelType<Object> untyped() {
        return UNTYPED;
    }

    public boolean isParameterized() {
        return wrapper instanceof ParameterizedTypeWrapper;
    }

    public ModelType<?> getRawType() {
        return Simple.typed(((ParameterizedTypeWrapper) wrapper).getRawType());
    }

    public ModelType<?> withArguments(List<ModelType<?>> types) {
        return Simple.typed(((ParameterizedTypeWrapper) wrapper).substituteAll(toWrappers(types)));
    }

    public boolean isGenericArray() {
        return wrapper instanceof GenericArrayTypeWrapper;
    }

    public ModelType<?> getComponentType() {
        return Simple.typed(((GenericArrayTypeWrapper) wrapper).getComponentType());
    }

    public List<ModelType<?>> getTypeVariables() {
        if (isParameterized()) {
            TypeWrapper[] typeArguments = ((ParameterizedTypeWrapper) wrapper).getActualTypeArguments();
            ImmutableList.Builder<ModelType<?>> builder = ImmutableList.builder();
            for (TypeWrapper typeArgument : typeArguments) {
                builder.add(Simple.typed(typeArgument));
            }
            return builder.build();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Casts this {@code ModelType} object to represent a subclass of the class
     * represented by the specified class object.  Checks that the cast
     * is valid, and throws a {@code ClassCastException} if it is not.  If
     * this method succeeds, it always returns a reference to this {@code ModelType} object.
     *
     * @throws ClassCastException if this cannot be cast as the subtype of the given type.
     * @throws IllegalStateException if this is a wildcard.
     * @throws IllegalArgumentException if the given type is a wildcard.
     */
    public <U> ModelType<? extends U> asSubtype(ModelType<U> modelType) {
        if (isWildcard()) {
            throw new IllegalStateException(this + " is a wildcard type");
        }
        if (modelType.isWildcard()) {
            throw new IllegalArgumentException(modelType + " is a wildcard type");
        }

        if (modelType.getRawClass().isAssignableFrom(getRawClass())) {
            return Cast.uncheckedCast(this);
        } else {
            throw new ClassCastException(String.format("'%s' cannot be cast as a subtype of '%s'", this, modelType));
        }
    }

    public boolean isAssignableFrom(ModelType<?> modelType) {
        return modelType == this || wrapper.isAssignableFrom(modelType.wrapper);
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotation) {
        return getRawClass().isAnnotationPresent(annotation);
    }

    public boolean isWildcard() {
        return getWildcardType() != null;
    }

    @Nullable
    public ModelType<?> getUpperBound() {
        WildcardWrapper wildcardType = getWildcardType();
        if (wildcardType == null) {
            return null;
        } else {
            ModelType<?> upperBound = Simple.typed(wildcardType.getUpperBound());
            if (upperBound.equals(UNTYPED)) {
                return null;
            }
            return upperBound;
        }
    }

    @Nullable
    public ModelType<?> getLowerBound() {
        WildcardWrapper wildcardType = getWildcardType();
        if (wildcardType == null) {
            return null;
        } else {
            TypeWrapper lowerBound = wildcardType.getLowerBound();
            if (lowerBound == null) {
                return null;
            }
            return Simple.typed(lowerBound);
        }
    }

    private WildcardWrapper getWildcardType() {
        if (wrapper instanceof WildcardWrapper) {
            return (WildcardWrapper) wrapper;
        }
        return null;
    }

    public boolean isHasWildcardTypeVariables() {
        if (isWildcard()) {
            return true;
        } else if (isParameterized()) {
            for (ModelType<?> typeVariable : getTypeVariables()) {
                if (typeVariable.isHasWildcardTypeVariables()) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Class<?>> getAllClasses() {
        ImmutableList.Builder<Class<?>> builder = ImmutableList.builder();
        wrapper.collectClasses(builder);
        return builder.build();
    }

    public String getName() {
        return wrapper.getRepresentation(true);
    }

    /**
     * Returns a human-readable name for the type.
     */
    public String getDisplayName() {
        return wrapper.getRepresentation(false);
    }

    public String toString() {
        return wrapper.getRepresentation(true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelType)) {
            return false;
        }

        ModelType<?> modelType = (ModelType<?>) o;

        return wrapper.equals(modelType.wrapper);
    }

    @Override
    public int hashCode() {
        return wrapper.hashCode();
    }

    abstract public static class Builder<T> {
        private ParameterizedTypeWrapper wrapper;

        public Builder() {
            wrapper = (ParameterizedTypeWrapper) wrap(new TypeToken<T>(getClass()) {
            }.getType());
        }

        @SuppressWarnings("unchecked")
        public <I> Builder<T> where(Parameter<I> parameter, ModelType<I> type) {
            wrapper = wrapper.substitute(parameter.typeVariable, type.wrapper);
            return this;
        }

        public ModelType<T> build() {
            return Simple.typed(wrapper);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    abstract public static class Parameter<T> {
        private final TypeVariable<?> typeVariable;

        public Parameter() {
            Type type = new TypeToken<T>(getClass()) {
            }.getType();
            if (type instanceof TypeVariable<?>) {
                this.typeVariable = (TypeVariable<?>) type;
            } else {
                throw new IllegalStateException("T for Parameter<T> MUST be a type variable");
            }
        }
    }

    private static final TypeWrapper[] EMPTY_TYPE_WRAPPER_ARRAY = new TypeWrapper[0];

    @Nullable
    private static TypeWrapper wrap(Type type) {
        if (type == null) {
            return null;
        } else if (type instanceof Class) {
            return new ClassTypeWrapper((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return new ParameterizedTypeWrapper(
                    toWrappers(parameterizedType.getActualTypeArguments()),
                    (ClassTypeWrapper) wrap(parameterizedType.getRawType()),
                    wrap(parameterizedType.getOwnerType())
            );
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            return new WildcardTypeWrapper(
                    toWrappers(wildcardType.getUpperBounds()),
                    toWrappers(wildcardType.getLowerBounds()),
                    type.hashCode()
            );
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVariable = (TypeVariable<?>) type;
            return new TypeVariableTypeWrapper(
                typeVariable.getName(),
                toWrappers(typeVariable.getBounds()),
                type.hashCode()
            );
        } else if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            return new GenericArrayTypeWrapper(wrap(genericArrayType.getGenericComponentType()), type.hashCode());
        } else {
            throw new IllegalArgumentException("cannot wrap type of type " + type.getClass());
        }
    }

    static TypeWrapper[] toWrappers(Type[] types) {
        if (types.length == 0) {
            return EMPTY_TYPE_WRAPPER_ARRAY;
        } else {
            TypeWrapper[] wrappers = new TypeWrapper[types.length];
            int i = 0;
            for (Type type : types) {
                wrappers[i++] = wrap(type);
            }
            return wrappers;
        }
    }

    static TypeWrapper[] toWrappers(List<ModelType<?>> types) {
        if (types.isEmpty()) {
            return EMPTY_TYPE_WRAPPER_ARRAY;
        } else {
            TypeWrapper[] wrappers = new TypeWrapper[types.size()];
            int i = 0;
            for (ModelType<?> type : types) {
                wrappers[i++] = type.wrapper;
            }
            return wrappers;
        }
    }

    private static class Simple<T> extends ModelType<T> {
        public static <T> ModelType<T> typed(Type type) {
            return new Simple<T>(type);
        }

        public static <T> ModelType<T> typed(TypeWrapper wrapper) {
            return new Simple<T>(wrapper);
        }

        public Simple(Type type) {
            super(wrap(type));
        }

        public Simple(TypeWrapper type) {
            super(type);
        }
    }

}
