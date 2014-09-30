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

import org.gradle.api.Nullable;

/**
 * A model reference is a speculative reference to a potential model element.
 * <p>
 * Rule subjects/inputs are defined in terms of references, as opposed to concrete identity.
 * The reference may be by type only, or by path only.
 * <p>
 * A reference doesn't include the notion of readonly vs. writable as the context of the reference implies this.
 * Having this be part of the reference would open opportunities for mismatch of that flag in the context.
 *
 * @param <T> the type of the reference.
 */
public class ModelReference<T> {

    private final ModelPath path;
    private final ModelType<T> type;
    private final String description;

    private ModelReference(@Nullable ModelPath path, ModelType<T> type, String description) {
        this.path = path;
        this.type = type;
        this.description = description;
    }

    public static <T> ModelReference<T> of(ModelPath path, ModelType<T> type, String description) {
        return new ModelReference<T>(path, type, description);
    }

    public static <T> ModelReference<T> of(ModelPath path, ModelType<T> type) {
        return new ModelReference<T>(path, type, null);
    }

    public static <T> ModelReference<T> of(String path, Class<T> type) {
        return of(ModelPath.path(path), ModelType.of(type));
    }

    public static <T> ModelReference<T> of(Class<T> type) {
        return of(null, ModelType.of(type));
    }

    public static <T> ModelReference<T> of(ModelType<T> type) {
        return of(null, type);
    }

    public static ModelReference<?> of(String path) {
        return of(ModelPath.path(path), ModelType.UNTYPED);
    }

    public static ModelReference<Object> untyped(ModelPath path) {
        return untyped(path, null);
    }

    public static ModelReference<Object> untyped(ModelPath path, String description) {
        return of(path, ModelType.UNTYPED, description);
    }

    @Nullable
    public ModelPath getPath() {
        return path;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public ModelType<T> getType() {
        return type;
    }

    public boolean isUntyped() {
        return type.equals(ModelType.UNTYPED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelReference<?> that = (ModelReference<?>) o;

        if(path == null){
            if (that.path == null){
                return type.equals(that.type);
            }
            return false;
        }
        return path.equals(that.path) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ModelReference{path=" + path + ", type=" + type + '}';
    }
}
