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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.Set;

@ThreadSafe
public class ModelProperty<T> {

    public enum StateManagementType {
        /**
         * The state of the property is stored as child nodes in the model.
         */
        MANAGED,

        /**
         * The state of the property is handled by the view.
         */
        UNMANAGED,

        /**
         * The state of the property is handled by an unmanaged delegate.
         */
        DELEGATED
    }

    private final String name;
    private final ModelType<T> type;
    private final StateManagementType stateManagementType;
    private final boolean writable;
    private final Set<ModelType<?>> declaredBy;
    private final List<WeaklyTypeReferencingMethod<?, T>> getters;

    private ModelProperty(ModelType<T> type, String name, StateManagementType stateManagementType, boolean writable, Set<ModelType<?>> declaredBy, List<WeaklyTypeReferencingMethod<?, T>> getters) {
        this.name = name;
        this.type = type;
        this.stateManagementType = stateManagementType;
        this.writable = writable;
        this.declaredBy = ImmutableSet.copyOf(declaredBy);
        this.getters = ImmutableList.copyOf(getters);
    }

    public static <T> ModelProperty<T> of(ModelType<T> type, String name, StateManagementType stateManagementType, boolean writable, Set<ModelType<?>> declaredBy, List<WeaklyTypeReferencingMethod<?, T>> getters) {
        return new ModelProperty<T>(type, name, stateManagementType, writable, declaredBy, getters);
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return type;
    }

    public StateManagementType getStateManagementType() {
        return stateManagementType;
    }

    public boolean isWritable() {
        return writable;
    }

    public Set<ModelType<?>> getDeclaredBy() {
        return declaredBy;
    }

    private WeaklyTypeReferencingMethod<?, T> firstGetter() {
        return getters.get(0);
    }

    public List<WeaklyTypeReferencingMethod<?, T>> getGetters() {
        return getters;
    }

    public <I> T getPropertyValue(I instance) {
        return Cast.<WeaklyTypeReferencingMethod<I, T>>uncheckedCast(firstGetter()).invoke(instance);
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
            && Objects.equal(this.stateManagementType, that.stateManagementType)
            && writable == that.writable;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + stateManagementType.hashCode();
        result = 31 * result + Boolean.valueOf(writable).hashCode();
        return result;
    }

    @Override
    public String toString() {
        return stateManagementType.name().toLowerCase() + " " + getName() + "(" + getType().getSimpleName() + ")";
    }
}
