/*
 * Copyright 2013 the original author or authors.
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
import com.google.common.collect.Lists;
import org.gradle.api.Nullable;

import java.util.List;

public class ModelBinding<T> {

    private final ModelType<T> type;
    private final ModelPath path;

    private ModelBinding(ModelType<T> type, @Nullable ModelPath path) {
        this.type = type;
        this.path = path;
    }

    public static <T> ModelBinding<T> of(ModelPath modelPath, ModelType<T> type) {
        return new ModelBinding<T>(type, modelPath);
    }

    public static <T> ModelBinding<T> of(ModelReference<T> reference) {
        return of(reference.getPath(), reference.getType());
    }

    public static <T> ModelBinding<T> of(String path, Class<T> type) {
        return of(new ModelPath(path), ModelType.of(type));
    }


    public ModelType<T> getType() {
        return type;
    }

    @Nullable
    public ModelPath getPath() {
        return path;
    }

    public boolean isBound() {
        return path != null;
    }

    public ModelBinding<T> bind(ModelPath path) {
        if (isBound()) {
            throw new IllegalStateException("Cannot bind model binding " + toString() + " to path '" + path + "' as it is already fully bound");
        }

        return of(path, type);
    }

    public ModelReference<T> getReference() {
        if (!isBound()) {
            throw new IllegalStateException("Binding is not bound");
        }

        return ModelReference.of(path, type);
    }

    @Override
    public String toString() {
        return "ModelBinding{type=" + type + ", path=" + path + '}';
    }

    public static List<ModelBinding<?>> toBindings(List<ModelReference<?>> references) {
        return Lists.transform(references, new Function<ModelReference<?>, ModelBinding<?>>() {
            @Nullable
            public ModelBinding<?> apply(ModelReference input) {
                return of(input);
            }
        });
    }

}
