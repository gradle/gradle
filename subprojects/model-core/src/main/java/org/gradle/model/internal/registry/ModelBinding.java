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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelPromise;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

/**
 * A binding of a reference to an actual model element.
 * <p>
 * A binding represents the knowledge that the model element referenced by the reference is known and can project a view of the reference type.
 */
abstract class ModelBinding {

    final BindingPredicate predicate;
    final ModelRuleDescriptor referrer;
    final boolean writable;
    protected ModelNodeInternal boundTo;

    protected ModelBinding(ModelRuleDescriptor referrer, BindingPredicate predicate, boolean writable) {
        this.predicate = predicate;
        this.referrer = referrer;
        this.writable = writable;
    }

    public BindingPredicate getPredicate() {
        return predicate;
    }

    public boolean isBound() {
        return boundTo != null;
    }

    public ModelNodeInternal getNode() {
        if (boundTo == null) {
            throw new IllegalStateException("Target node has not been bound.");
        }
        return boundTo;
    }

    boolean isTypeCompatible(ModelPromise promise) {
        return promise.canBeViewedAsWritable(predicate.getType()) || promise.canBeViewedAsReadOnly(predicate.getType());
    }

    @Override
    public String toString() {
        return "ModelBinding{predicate=" + predicate + ", node=" + boundTo + '}';
    }

    public abstract void onCreate(ModelNodeInternal node);

    public void onRemove(ModelNodeInternal node) {
        if (node == boundTo) {
            boundTo = null;
        }
    }
}
