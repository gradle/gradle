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

package org.gradle.model.internal.registry;

import org.gradle.api.Nullable;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;

/**
 * A fixed set of criteria to use to bind a single model element.
 */
public class BindingPredicate extends ModelPredicate {
    private final ModelReference<?> reference;

    public BindingPredicate(ModelReference<?> reference) {
        this.reference = reference;
    }

    public ModelReference<?> getReference() {
        return reference;
    }

    @Override
    public String toString() {
        ModelType<?> type = reference.getType();
        return "{type: " + (type == null ? null : type.getDisplayName()) + ", path: " + getPath() + ", scope: " + getScope() + ", state: " + getState() + "}";
    }

    @Nullable
    @Override
    public ModelPath getPath() {
        return reference.getPath();
    }

    public ModelType<?> getType() {
        return reference.getType();
    }

    public boolean matches(MutableModelNode node) {
        return reference.isUntyped() || node.canBeViewedAs(reference.getType());
    }

    @Nullable
    public ModelPath getScope() {
        return reference.getScope();
    }

    public ModelNode.State getState() {
        return reference.getState();
    }
}
