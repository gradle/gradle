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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeResolver;
import com.google.common.reflect.TypeToken;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A type token for the type of a model element.
 * <p>
 * Borrows from Guava's type token.
 * Represent a fully resolved/bound type.
 */
@ThreadSafe
public abstract class ModelType<T> {

    // TODO analyze performance cost of wrapping Guava's type token instead of inlining the code
    // TODO there is no handling of TypeVariable here - at least need some validation that the incoming Type is not a variable

    private static class Simple<T> extends ModelType<T> {
        private Simple(TypeToken<T> typeToken) {
            super(typeToken);
        }
    }

    public static final ModelType<Object> UNTYPED = ModelType.of(Object.class);

    private final TypeToken<T> typeToken;

    private ModelType(TypeToken<T> typeToken) {
        this.typeToken = typeToken;
    }

    protected ModelType() {
        typeToken = new TypeToken<T>(getClass()) {
        };
    }

    protected ModelType(final Class<?> clazz) {
        Type superclass = getClass().getGenericSuperclass();
        checkArgument(superclass instanceof ParameterizedType, "%s isn't parameterized", superclass);
        @SuppressWarnings("ConstantConditions") Type captured = ((ParameterizedType) superclass).getActualTypeArguments()[0];

        if (captured instanceof Class) {
            this.typeToken = Cast.uncheckedCast(TypeToken.of(captured));
        } else {
            this.typeToken = Cast.uncheckedCast(TypeToken.of(clazz).resolveType(captured));
        }
    }

    public static ModelType<Object> untyped() {
        return UNTYPED;
    }

    public static <T> ModelType<T> of(Class<T> clazz) {
        return new Simple<T>(TypeToken.of(clazz));
    }

    public static <T> ModelType<T> typeOf(T instance) {
        @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) instance.getClass();
        return of(clazz);
    }

    public static ModelType<?> of(Type type) {
        return toModelType(TypeToken.of(type));
    }

    private static <T> ModelType<T> toModelType(TypeToken<T> type) {
        return new Simple<T>(type);
    }

    public Class<? super T> getRawClass() {
        return typeToken.getRawType();
    }

    public Class<T> getConcreteClass() {
        @SuppressWarnings("unchecked")
        Class<T> concreteClass = (Class<T>) getRawClass();
        return concreteClass;
    }

    public boolean isParameterized() {
        return typeToken.getType() instanceof ParameterizedType;
    }

    public List<ModelType<?>> getTypeVariables() {
        if (isParameterized()) {
            List<Type> types = Arrays.asList(((ParameterizedType) typeToken.getType()).getActualTypeArguments());
            return ImmutableList.<ModelType<?>>builder().addAll(Iterables.transform(types, new Function<Type, ModelType<?>>() {
                public ModelType<?> apply(Type input) {
                    return ModelType.of(input);
                }
            })).build();
        } else {
            return Collections.emptyList();
        }
    }

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
        return typeToken.isAssignableFrom(modelType.typeToken);
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
        Type type = typeToken.getType();
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

    @Override
    public String toString() {
        return typeToken.toString();
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

        return typeToken.equals(modelType.typeToken);
    }

    @Override
    public int hashCode() {
        return typeToken.hashCode();
    }

    abstract public static class Builder<T> {
        private TypeToken<T> typeToken;

        public Builder() {
            typeToken = new TypeToken<T>(getClass()) {
            };
        }

        @SuppressWarnings("unchecked")
        public <I> Builder<T> where(Parameter<I> parameter, ModelType<I> type) {
            TypeResolver resolver = new TypeResolver().where(parameter.typeVariable, type.typeToken.getType());
            typeToken = (TypeToken<T>) TypeToken.of(resolver.resolveType(typeToken.getType()));
            return this;
        }

        public ModelType<T> build() {
            return new Simple<T>(typeToken);
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
}
