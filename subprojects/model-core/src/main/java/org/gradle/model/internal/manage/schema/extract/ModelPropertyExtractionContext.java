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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.reflect.PropertyAccessorType.hasGetter;
import static org.gradle.internal.reflect.PropertyAccessorType.hasSetter;

public class ModelPropertyExtractionContext {

    private final String propertyName;
    private Map<PropertyAccessorType, PropertyAccessorExtractionContext> accessors;

    public ModelPropertyExtractionContext(String propertyName) {
        this.propertyName = propertyName;
        this.accessors = Maps.newEnumMap(PropertyAccessorType.class);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public boolean isReadable() {
        return hasGetter(accessors.keySet());
    }

    public boolean isWritable() {
        return hasSetter(accessors.keySet());
    }

    public void addAccessor(PropertyAccessorExtractionContext accessor) {
        PropertyAccessorType type = accessor.getAccessorType();
        // TODO:LPTR What happens when the property has multiple accessors in the same role but with different type?
        // if (accessors.containsKey(type)) {
        //     throw new IllegalStateException("Accessor already registered: " + type + " " + accessor);
        // }
        accessors.put(type, accessor);
    }

    @Nullable
    public PropertyAccessorExtractionContext getAccessor(PropertyAccessorType type) {
        return accessors.get(type);
    }

    public Collection<PropertyAccessorExtractionContext> getAccessors() {
        return accessors.values();
    }

    public void dropInvalidAccessor(PropertyAccessorType type, ImmutableCollection.Builder<Method> droppedMethods) {
        PropertyAccessorExtractionContext removedAccessor = accessors.remove(type);
        if (removedAccessor != null) {
            droppedMethods.add(removedAccessor.getMostSpecificDeclaration());
        }
    }

    public Set<ModelType<?>> getDeclaredBy() {
        ImmutableSortedSet.Builder<ModelType<?>> declaredBy = new ImmutableSortedSet.Builder<ModelType<?>>(Ordering.usingToString());
        for (PropertyAccessorExtractionContext accessor : accessors.values()) {
            for (Method method : accessor.getDeclaringMethods()) {
                declaredBy.add(ModelType.declaringType(method));
            }
        }
        return declaredBy.build();
    }

    @Nullable
    public PropertyAccessorExtractionContext mergeGetters() {
        PropertyAccessorExtractionContext getGetter = getAccessor(PropertyAccessorType.GET_GETTER);
        PropertyAccessorExtractionContext isGetter = getAccessor(PropertyAccessorType.IS_GETTER);
        if (getGetter == null && isGetter == null) {
            return null;
        }
        Iterable<Method> getMethods = getGetter != null ? getGetter.getDeclaringMethods() : Collections.<Method>emptyList();
        Iterable<Method> isMethods = isGetter != null ? isGetter.getDeclaringMethods() : Collections.<Method>emptyList();
        return new PropertyAccessorExtractionContext(PropertyAccessorType.GET_GETTER, Iterables.concat(getMethods, isMethods));
    }
}
