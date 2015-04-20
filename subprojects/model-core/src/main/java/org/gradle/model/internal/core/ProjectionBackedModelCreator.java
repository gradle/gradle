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
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

@ThreadSafe
public class ProjectionBackedModelCreator implements ModelCreator {
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final boolean ephemeral;
    private final boolean hidden;
    private final ModelProjection projection;
    private final List<? extends ModelReference<?>> inputs;
    private final BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer;

    public ProjectionBackedModelCreator(
            ModelPath path,
            ModelRuleDescriptor descriptor,
            boolean ephemeral,
            boolean hidden,
            List<? extends ModelReference<?>> inputs,
            ModelProjection projection,
            BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer
    ) {
        this.path = path;
        this.descriptor = descriptor;
        this.ephemeral = ephemeral;
        this.hidden = hidden;
        this.projection = projection;
        this.inputs = inputs;
        this.initializer = initializer;
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return projection;
    }

    public ModelAdapter getAdapter() {
        return projection;
    }

    public void create(MutableModelNode node, List<ModelView<?>> inputs) {
        node.setHidden(hidden);
        initializer.execute(node, inputs);
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    public List<? extends ModelReference<?>> getInputs() {
        return inputs;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

}
