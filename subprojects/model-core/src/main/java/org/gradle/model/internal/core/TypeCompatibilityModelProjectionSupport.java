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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

@ThreadSafe
public abstract class TypeCompatibilityModelProjectionSupport<M> implements ModelProjection {

    private final ModelType<M> type;
    private final boolean canBeViewedAsReadOnly;
    private final boolean canBeViewedAsWritable;

    public TypeCompatibilityModelProjectionSupport(ModelType<M> type, boolean canBeViewedAsReadOnly, boolean canBeViewedAsWritable) {
        this.type = type;
        this.canBeViewedAsReadOnly = canBeViewedAsReadOnly;
        this.canBeViewedAsWritable = canBeViewedAsWritable;
    }

    protected ModelType<M> getType() {
        return type;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return canBeViewedAsWritable && canBeAssignedTo(targetType);
    }

    private <T> boolean canBeAssignedTo(ModelType<T> targetType) {
        return targetType.isAssignableFrom(type) || (targetType== ModelType.UNTYPED && type.getRawClass().isPrimitive());
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> targetType) {
        return canBeViewedAsReadOnly && canBeAssignedTo(targetType);
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> type, MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        if (canBeViewedAsWritable(type)) {
            return Cast.uncheckedCast(toView(modelNode, ruleDescriptor, true));
        } else {
            return null;
        }
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAsReadOnly(type)) {
            return Cast.uncheckedCast(toView(modelNode, ruleDescriptor, false));
        } else {
            return null;
        }
    }

    protected abstract ModelView<M> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable);

    public Iterable<String> getWritableTypeDescriptions(MutableModelNode node) {
        if (canBeViewedAsWritable) {
            return Collections.singleton(description(type));
        } else {
            return Collections.emptySet();
        }
    }

    public Iterable<String> getReadableTypeDescriptions(MutableModelNode node) {
        if (canBeViewedAsReadOnly) {
            return Collections.singleton(description(type));
        } else {
            return Collections.emptySet();
        }
    }

    public static String description(ModelType<?> type) {
        if (type.getRawClass().getSuperclass() == null && type.getRawClass().getInterfaces().length == 0) {
            return type.toString();
        }
        return type.toString() + " (or assignment compatible type thereof)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypeCompatibilityModelProjectionSupport<?> that = (TypeCompatibilityModelProjectionSupport<?>) o;
        return canBeViewedAsReadOnly == that.canBeViewedAsReadOnly && canBeViewedAsWritable == that.canBeViewedAsWritable && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (canBeViewedAsReadOnly ? 1 : 0);
        result = 31 * result + (canBeViewedAsWritable ? 1 : 0);
        return result;
    }
}
