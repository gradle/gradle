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

package org.gradle.model.internal.inspect;

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

class BiActionBackedModelAction<T> implements ModelAction<T> {
    private final ModelReference<T> modelReference;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputs;
    private final BiAction<? super T, ? super Inputs> initializer;

    public BiActionBackedModelAction(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, BiAction<? super T, ? super Inputs> initializer) {
        this.modelReference = modelReference;
        this.descriptor = descriptor;
        this.inputs = inputs;
        this.initializer = initializer;
    }

    @Override
    public ModelReference<T> getSubject() {
        return modelReference;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public List<ModelReference<?>> getInputs() {
        return inputs;
    }

    @Override
    public void execute(MutableModelNode modelNode, T object, Inputs inputs, ModelRuleSourceApplicator modelRuleSourceApplicator, ModelRegistrar modelRegistrar,
                        PluginClassApplicator pluginClassApplicator) {
        initializer.execute(object, inputs);
    }
}
