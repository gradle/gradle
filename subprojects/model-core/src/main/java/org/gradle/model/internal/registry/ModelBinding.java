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

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelReference;

/**
 * A binding of a reference to an actual model element.
 * <p>
 * A binding represents the knowledge that the model element referenced by the reference is known and can project a view of the reference type.
 * Like the reference, whether the view is read or write is not inherent in the binding and is contextual.
 */
@ThreadSafe
class ModelBinding {

    private final ModelNodeInternal node;
    private final ModelReference<?> reference;

    private ModelBinding(ModelReference<?> reference, ModelNodeInternal node) {
        this.node = node;
        this.reference = reference;
    }

    public static ModelBinding of(ModelReference<?> reference, ModelNodeInternal modelNode) {
        return new ModelBinding(reference, modelNode);
    }

    public ModelReference<?> getReference() {
        return reference;
    }

    public ModelNodeInternal getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "ModelBinding{reference=" + reference + ", node=" + node + '}';
    }

}
