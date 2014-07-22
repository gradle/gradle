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

/**
 * A binding of a reference to an actual model element.
 * <p>
 * A binding represents the knowledge that the model element referenced by the reference is known and can project a view of the reference type.
 * Like the reference, whether the view is read or write is not inherent in the binding and is contextual.
 */
public class ModelBinding<T> {

    private final ModelReference<T> reference;
    private final ModelPath path;

    private ModelBinding(ModelReference<T> reference, ModelPath path) {
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        }
        this.reference = reference;
        this.path = path;
    }

    public static <T> ModelBinding<T> of(ModelReference<T> reference) {
        if (reference.getPath() == null) {
            throw new IllegalArgumentException("reference has no path: " + reference);
        }

        return new ModelBinding<T>(reference, reference.getPath());
    }

    public static <T> ModelBinding<T> of(ModelReference<T> reference, ModelPath path) {
        if (reference.getPath() != null && !reference.getPath().equals(path)) {
            throw new IllegalArgumentException("mismatched paths: " + reference.getPath() + " & " + path);
        } else {
            return new ModelBinding<T>(reference, path);
        }
    }

    public ModelReference<T> getReference() {
        return reference;
    }

    public ModelPath getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "ModelBinding{reference=" + reference + ", path=" + path + '}';
    }
}
