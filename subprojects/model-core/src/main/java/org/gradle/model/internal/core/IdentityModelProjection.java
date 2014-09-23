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

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;

public class IdentityModelProjection<M> implements ModelProjection<M> {

    private final ModelType<? super M> type;
    private final boolean canBeViewedAsReadOnly;
    private final boolean canBeViewedAsWritable;

    public IdentityModelProjection(ModelType<? super M> type, boolean canBeViewedAsReadOnly, boolean canBeViewedAsWritable) {
        this.type = type;
        this.canBeViewedAsReadOnly = canBeViewedAsReadOnly;
        this.canBeViewedAsWritable = canBeViewedAsWritable;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return canBeViewedAsWritable && targetType.isAssignableFrom(type);
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> targetType) {
        return canBeViewedAsReadOnly && targetType.isAssignableFrom(type);
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> reference, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRegistry, M instance) {
        if (canBeViewedAsWritable(reference.getReference().getType())) {
            return view(instance);
        } else {
            return null;
        }
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> targetType, M instance) {
        if (canBeViewedAsReadOnly(targetType)) {
            return view(instance);
        } else {
            return null;
        }
    }

    private <T> ModelView<? extends T> view(M instance) {
        @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(type, instance);
        return cast;
    }

    public Iterable<String> getWritableTypeDescriptions() {
        if (canBeViewedAsWritable) {
            return Collections.singleton(description(type));
        } else {
            return Collections.emptySet();
        }
    }

    public Iterable<String> getReadableTypeDescriptions() {
        if (canBeViewedAsReadOnly) {
            return Collections.singleton(description(type));
        } else {
            return Collections.emptySet();
        }
    }

    public static String description(ModelType<?> type) {
        return type.toString() + " (or assignment compatible type thereof)";
    }
}
