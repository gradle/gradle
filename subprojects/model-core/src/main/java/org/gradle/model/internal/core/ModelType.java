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
import com.google.common.reflect.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// TODO restrict this to concrete types, validated recursively
public class ModelType<T> {

    private final TypeToken<T> typeToken;

    public ModelType(TypeToken<T> typeToken) {
        this.typeToken = typeToken;
    }

    public ModelType(Class<T> clazz) {
        this(TypeToken.of(clazz));
    }

    public static <T> ModelType<T> of(Class<T> clazz) {
        return new ModelType<T>(clazz);
    }

    public static <T> ModelType<T> of(TypeToken<T> type) {
        return new ModelType<T>(type);
    }

    public Class<? super T> getRawClass() {
        return typeToken.getRawType();
    }

    public TypeToken<T> getToken() {
        return typeToken;
    }

    public boolean isParameterized() {
        return typeToken.getType() instanceof ParameterizedType;
    }

    public List<ModelType<?>> getTypeVariables() {
        if (isParameterized()) {
            List<Type> types = Arrays.asList(((ParameterizedType) typeToken.getType()).getActualTypeArguments());
            return ImmutableList.<ModelType<?>>builder().addAll(Iterables.transform(types, new Function<Type, ModelType<?>>() {
                public ModelType<?> apply(Type input) {
                    @SuppressWarnings("unchecked") ModelType raw = new ModelType(TypeToken.of(input));
                    return raw;
                }
            })).build();
        } else {
            return Collections.emptyList();
        }
    }

    public boolean isAssignableFrom(ModelType<?> modelType) {
        return typeToken.isAssignableFrom(modelType.typeToken);
    }

    @Override
    public String toString() {
        return "ModelType{" + typeToken + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelType modelType = (ModelType) o;

        return typeToken.equals(modelType.typeToken);
    }

    @Override
    public int hashCode() {
        return typeToken.hashCode();
    }
}
