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

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;

import static org.gradle.model.internal.manage.schema.extract.PrimitiveTypes.isPrimitiveType;

@ThreadSafe
public abstract class TypeCompatibilityModelProjectionSupport<M> implements ModelProjection {

    private final ModelType<M> type;

    public TypeCompatibilityModelProjectionSupport(ModelType<M> type) {
        this.type = type;
    }

    protected ModelType<M> getType() {
        return type;
    }

    @Override
    public <T> boolean canBeViewedAs(ModelType<T> targetType) {
        return canBeAssignedTo(targetType);
    }

    private <T> boolean canBeAssignedTo(ModelType<T> targetType) {
        return targetType.isAssignableFrom(type)
            || (targetType == ModelType.UNTYPED && isPrimitiveType(type));
    }

    @Override
    public <T> ModelView<? extends T> asMutable(ModelType<T> type, MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAs(type)) {
            return Cast.uncheckedCast(toView(modelNode, ruleDescriptor, true));
        } else {
            return null;
        }
    }

    @Override
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAs(type)) {
            return Cast.uncheckedCast(toView(modelNode, ruleDescriptor, false));
        } else {
            return null;
        }
    }

    protected abstract ModelView<M> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable);

    @Override
    public Iterable<String> getTypeDescriptions(MutableModelNode node) {
        return Collections.singleton(description(type));
    }

    protected String toStringValueDescription(Object instance) {
        String valueDescription = instance.toString();
        if (valueDescription != null) {
            return valueDescription;
        }
        return new StringBuilder(type.toString()).append("#toString() returned null").toString();
    }

    public static String description(ModelType<?> type) {
        if (type.getRawClass().getSuperclass() == null && type.getRawClass().getInterfaces().length == 0) {
            return type.toString();
        }
        return type.toString() + " (or assignment compatible type thereof)";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + type + "]";
    }
}
