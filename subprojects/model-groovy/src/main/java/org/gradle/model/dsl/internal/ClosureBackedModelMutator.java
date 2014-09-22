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

package org.gradle.model.dsl.internal;

import groovy.lang.Closure;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking;
import org.gradle.model.dsl.internal.transform.SourceLocation;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.List;

class ClosureBackedModelMutator implements ModelMutator<Object> {

    private final Closure<?> action;
    private final List<ModelReference<?>> inputReferences;
    private final ModelPath modelPath;
    private final SourceLocation sourceLocation;

    public ClosureBackedModelMutator(Closure<?> action, List<ModelReference<?>> inputReferences, ModelPath modelPath, SourceLocation sourceLocation) {
        this.action = action;
        this.inputReferences = inputReferences;
        this.modelPath = modelPath;
        this.sourceLocation = sourceLocation;
    }

    public ModelReference<Object> getSubject() {
        return ModelReference.untyped(modelPath);
    }

    public void mutate(final Object object, Inputs inputs) {
        RuleInputAccessBacking.runWithContext(inputs, new Runnable() {
            public void run() {
                new ClosureBackedAction<Object>(action).execute(object);
            }
        });
    }

    public ModelRuleDescriptor getDescriptor() {
        String descriptor = String.format("model.%s @ %s", modelPath, sourceLocation);
        return new SimpleModelRuleDescriptor(descriptor);
    }

    public List<ModelReference<?>> getInputs() {
        return inputReferences;
    }
}
