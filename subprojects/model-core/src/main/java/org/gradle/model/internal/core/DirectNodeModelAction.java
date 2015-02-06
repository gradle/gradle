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

import org.gradle.api.Action;
import org.gradle.internal.BiAction;
import org.gradle.internal.TriAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

public class DirectNodeModelAction<T> implements ModelAction<T> {

    private final ModelReference<T> subjectReference;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputs;
    private final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action;

    private DirectNodeModelAction(ModelReference<T> subjectReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        this.subjectReference = subjectReference;
        this.inputs = inputs;
        this.action = action;
        this.descriptor = descriptor;
    }

    public DirectNodeModelAction(ModelReference<T> reference, ModelRuleDescriptor descriptor, final BiAction<? super MutableModelNode, ? super T> action) {
        this(reference, descriptor, ModelReference.NONE, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode modelNode, T t, List<ModelView<?>> modelViews) {
                action.execute(modelNode, t);
            }
        });
    }

    public static <T> ModelAction<T> of(ModelReference<T> reference, ModelRuleDescriptor descriptor, final Action<? super MutableModelNode> action) {
        return new DirectNodeModelAction<T>(reference, descriptor, new BiAction<MutableModelNode, T>() {
            @Override
            public void execute(MutableModelNode modelNode, T t) {
                action.execute(modelNode);
            }
        });
    }

    public static ModelAction<?> of(ModelPath path, ModelRuleDescriptor descriptor, final Action<? super MutableModelNode> action) {
        return of(ModelReference.of(path), descriptor, action);
    }

    public static <T> ModelAction<T> of(ModelReference<T> reference, ModelRuleDescriptor descriptor, BiAction<? super MutableModelNode, ? super T> action) {
        return new DirectNodeModelAction<T>(reference, descriptor, action);
    }

    public static <T, I> ModelAction<?> of(ModelReference<T> reference, ModelRuleDescriptor descriptor, final ModelReference<I> input, final BiAction<? super MutableModelNode, ? super I> action) {
        return new DirectNodeModelAction<T>(reference, descriptor, Collections.<ModelReference<?>>singletonList(input), new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode modelNode, T t, List<ModelView<?>> modelViews) {
                action.execute(modelNode, ModelViews.getInstance(modelViews.get(0), input));
            }
        });
    }

    public static <I> ModelAction<?> of(ModelPath path, ModelRuleDescriptor descriptor, ModelReference<I> input, BiAction<? super MutableModelNode, ? super I> action) {
        return of(ModelReference.of(path), descriptor, input, action);
    }

    @Override
    public ModelReference<T> getSubject() {
        return subjectReference;
    }

    @Override
    public void execute(MutableModelNode modelNode, T object, List<ModelView<?>> inputs) {
        action.execute(modelNode, object, inputs);
    }

    @Override
    public List<ModelReference<?>> getInputs() {
        return inputs;
    }

    @Override
    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }
}
