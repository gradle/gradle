/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.base.Objects;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Wrapper for a {@link TypeVariable}, designed to be used only to represent a type variable in a
 * {@link org.gradle.model.internal.manage.schema.cache.WeakClassSet}. It ignores annotations on
 * on the type variable, and throws an {@link UnsupportedOperationException} if
 * {@link #getGenericDeclaration()} is called.
 */
public class TypeVariableTypeWrapper<D extends GenericDeclaration> implements TypeWrapper, TypeVariable<D> {
    private final String name;
    private final TypeWrapper[] bounds;
    private final int hashCode;

    public TypeVariableTypeWrapper(String name, TypeWrapper[] bounds, int hashCode) {
        this.name = name;
        this.bounds = bounds;
        this.hashCode = hashCode;
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public String getRepresentation(boolean full) {
        return name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type[] getBounds() {
        return ModelType.unwrap(bounds);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeVariable)) {
            return false;
        } else {
            TypeVariable<?> var2 = (TypeVariable<?>) o;
            return Objects.equal(this.getName(), var2.getName())
                && Arrays.equals(this.getBounds(), var2.getBounds());
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return null;
    }

    @Override
    public AnnotatedType[] getAnnotatedBounds() {
        return new AnnotatedType[0];
    }

    @Override
    public D getGenericDeclaration() {
        throw new UnsupportedOperationException();
    }
}
