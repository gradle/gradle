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

package org.gradle.model.internal.manage.schema;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.reflect.PropertyAccessorType.hasSetter;

@ThreadSafe
public class ModelProperty<T> {

    private final String name;
    private final ModelType<T> type;
    private final Set<ModelType<?>> declaredBy;
    private final ImmutableMap<PropertyAccessorType, WeaklyTypeReferencingMethod<?, ?>> accessors;
    private ModelSchema<T> schema;

    public ModelProperty(ModelType<T> type, String name, Set<ModelType<?>> declaredBy,
                         Map<PropertyAccessorType, WeaklyTypeReferencingMethod<?, ?>> accessors) {
        this.name = name;
        this.type = type;
        this.declaredBy = ImmutableSet.copyOf(declaredBy);
        this.accessors = Maps.immutableEnumMap(accessors);
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return type;
    }

    public ModelSchema<T> getSchema() {
        return schema;
    }

    public void setSchema(ModelSchema<T> schema) {
        this.schema = schema;
    }

    public boolean isWritable() {
        return hasSetter(accessors.keySet());
    }

    public Set<ModelType<?>> getDeclaredBy() {
        return declaredBy;
    }

    @Nullable
    public WeaklyTypeReferencingMethod<?, ?> getAccessor(PropertyAccessorType accessorType) {
        return accessors.get(accessorType);
    }

    public Collection<WeaklyTypeReferencingMethod<?, ?>> getAccessors() {
        return accessors.values();
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return isAnnotationPresent(annotationType, getAccessor(PropertyAccessorType.GET_GETTER))
            || isAnnotationPresent(annotationType, getAccessor(PropertyAccessorType.IS_GETTER));
    }

    private boolean isAnnotationPresent(Class<? extends Annotation> annotationType, WeaklyTypeReferencingMethod<?, ?> getter) {
        return getter != null && getter.getMethod().isAnnotationPresent(annotationType);
    }

    public <I> T getPropertyValue(I instance) {
        return Cast.<WeaklyTypeReferencingMethod<I, T>>uncheckedCast(getGetter()).invoke(instance);
    }

    public WeaklyTypeReferencingMethod<?, ?> getGetter() {
        WeaklyTypeReferencingMethod<?, ?> getter = getAccessor(PropertyAccessorType.GET_GETTER);
        if (getter == null) {
            getter = getAccessor(PropertyAccessorType.IS_GETTER);
        }
        if (getter == null) {
            throw new IllegalStateException("No getter for property" + this);
        }
        return getter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelProperty<?> that = (ModelProperty<?>) o;

        return Objects.equal(this.name, that.name)
            && Objects.equal(this.type, that.type)
            && isWritable() == that.isWritable();
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + Boolean.valueOf(isWritable()).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getName() + "(" + getType().getDisplayName() + ")";
    }
}
