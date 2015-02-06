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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeResolver;
import com.google.common.reflect.TypeToken;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.*;
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

    private TypeToken<T> getTypeToken() {
        return Cast.uncheckedCast(TypeToken.of(getType()));
    }

    public static <T> ModelType<T> of(Class<T> clazz) {
        return new Simple<T>(clazz);
    }

    public static <T> ModelType<T> returnType(Method method) {
        return new Simple<T>(method.getGenericReturnType());
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

    public Class<? super T> getRawClass() {
        return getTypeToken().getRawType();
    }

    public Class<T> getConcreteClass() {
        return Cast.uncheckedCast(getRawClass());
    }

    public boolean isRawClassOfParameterizedType() {
        Type type = getType();
        return type instanceof Class && ((Class) type).getTypeParameters().length > 0;
    }

    public Type getType() {
        return wrapper.unwrap();
    }

    public static ModelType<Object> untyped() {
        return UNTYPED;
    }

    public boolean isParameterized() {
        return getType() instanceof ParameterizedType;
    }

    public List<ModelType<?>> getTypeVariables() {
        if (isParameterized()) {
            Type[] typeArguments = ((ParameterizedType) getType()).getActualTypeArguments();
            ImmutableList.Builder<ModelType<?>> builder = ImmutableList.builder();
            for (Type typeArgument : typeArguments) {
                builder.add(of(typeArgument));
            }
            return builder.build();
        } else {
            return Collections.emptyList();
        }
    }

    @Nullable
    public ModelType<? extends T> asSubclass(ModelType<?> modelType) {
        if (isWildcard() || modelType.isWildcard()) {
            return null;
        }

        Class<? super T> thisClass = getRawClass();
        Class<?> otherClass = modelType.getRawClass();
        boolean isSubclass = thisClass.isAssignableFrom(otherClass) && !thisClass.equals(otherClass);

        if (isSubclass) {
            @SuppressWarnings("unchecked") ModelType<? extends T> cast = (ModelType<? extends T>) modelType;
            return cast;
        } else {
            return null;
        }
    }

    public boolean isAssignableFrom(ModelType<?> modelType) {
        return getTypeToken().isAssignableFrom(modelType.getTypeToken());
    }

    public boolean isWildcard() {
        return getWildcardType() != null;
    }

    public ModelType<?> getUpperBound() {
        WildcardType wildcardType = getWildcardType();
        if (wildcardType == null) {
            return null;
        } else {
            ModelType<?> upperBoundType = ModelType.of(wildcardType.getUpperBounds()[0]);
            if (upperBoundType.equals(UNTYPED)) {
                return null;
            } else {
                return upperBoundType;
            }
        }
    }

    public ModelType<?> getLowerBound() {
        WildcardType wildcardType = getWildcardType();
        if (wildcardType == null) {
            return null;
        } else {
            Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length == 0) {
                return null;
            } else {
                return ModelType.of(lowerBounds[0]);
            }
        }
    }

    private WildcardType getWildcardType() {
        Type type = getType();
        if (type instanceof WildcardType) {
            return (WildcardType) type;
        } else {
            return null;
        }
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
        addAllClasses(builder);
        return builder.build();
    }

    private void addAllClasses(ImmutableCollection.Builder<Class<?>> builder) {
        Type runtimeType = getType();
        if (runtimeType instanceof Class) {
            builder.add((Class<?>) runtimeType);
        } else if (runtimeType instanceof ParameterizedType) {
            builder.add((Class<?>) ((ParameterizedType) runtimeType).getRawType());
            for (Type type : ((ParameterizedType) runtimeType).getActualTypeArguments()) {
                ModelType.of(type).addAllClasses(builder);
            }
        } else if (runtimeType instanceof WildcardType) {
            for (Type type : ((WildcardType) runtimeType).getLowerBounds()) {
                ModelType.of(type).addAllClasses(builder);
            }
            for (Type type : ((WildcardType) runtimeType).getUpperBounds()) {
                ModelType.of(type).addAllClasses(builder);
            }
        } else {
            throw new IllegalArgumentException("Unable to deal with type " + runtimeType + " (" + runtimeType.getClass() + ")");
        }
    }

    public String toString() {
        return wrapper.getRepresentation();
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

        return getType().equals(modelType.getType());
    }

    @Override
    public int hashCode() {
        return getTypeToken().hashCode();
    }

    abstract public static class Builder<T> {
        private TypeToken<T> typeToken;

        public Builder() {
            typeToken = new TypeToken<T>(getClass()) {
            };
        }

        @SuppressWarnings("unchecked")
        public <I> Builder<T> where(Parameter<I> parameter, ModelType<I> type) {
            TypeResolver resolver = new TypeResolver().where(parameter.typeVariable, type.getTypeToken().getType());
            typeToken = (TypeToken<T>) TypeToken.of(resolver.resolveType(typeToken.getType()));
            return this;
        }

        public ModelType<T> build() {
            return Simple.typed(typeToken.getType());
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

    public static abstract class Specs {
        public static Spec<ModelType<?>> isAssignableTo(final ModelType<?> type) {
            return new Spec<ModelType<?>>() {
                public boolean isSatisfiedBy(ModelType<?> element) {
                    return type.isAssignableFrom(element);
                }
            };
        }

        public static Spec<ModelType<?>> isAssignableFrom(final ModelType<?> type) {
            return new Spec<ModelType<?>>() {
                public boolean isSatisfiedBy(ModelType<?> element) {
                    return element.isAssignableFrom(type);
                }
            };
        }

        public static Spec<ModelType<?>> isAssignableToAny(final Iterable<? extends ModelType<?>> types) {
            return new Spec<ModelType<?>>() {
                public boolean isSatisfiedBy(ModelType<?> element) {
                    return CollectionUtils.any(types, isAssignableFrom(element));
                }
            };
        }
    }

    private static final Type[] EMPTY_TYPE_ARRAY = new Type[0];
    private static final TypeWrapper[] EMPTY_TYPE_WRAPPER_ARRAY = new TypeWrapper[0];

    private static TypeWrapper wrap(Type type) {
        if (type == null) {
            return NullTypeWrapper.INSTANCE;
        } else if (type instanceof Class) {
            return new ClassTypeWrapper((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return new ParameterizedTypeWrapper(
                    toWrappers(parameterizedType.getActualTypeArguments()),
                    wrap(parameterizedType.getRawType()),
                    wrap(parameterizedType.getOwnerType()),
                    type.hashCode()
            );
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            return new WildcardTypeWrapper(
                    toWrappers(wildcardType.getUpperBounds()),
                    toWrappers(wildcardType.getLowerBounds()),
                    type.hashCode()
            );
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

    static Type[] unwrap(TypeWrapper[] wrappers) {
        if (wrappers.length == 0) {
            return EMPTY_TYPE_ARRAY;
        } else {
            Type[] types = new Type[wrappers.length];
            int i = 0;
            for (TypeWrapper wrapper : wrappers) {
                types[i++] = wrapper.unwrap();
            }
            return types;
        }
    }

    private static class Simple<T> extends ModelType<T> {
        public static <T> ModelType<T> typed(Type type) {
            return new Simple<T>(type);
        }

        public Simple(Type type) {
            super(wrap(type));
        }
    }

}
