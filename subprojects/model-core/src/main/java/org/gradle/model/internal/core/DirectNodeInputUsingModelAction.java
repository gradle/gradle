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

package org.gradle.model.internal.core;

import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.internal.TriAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

public class DirectNodeInputUsingModelAction<T> extends AbstractModelActionWithView<T> {
    private final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action;

    public DirectNodeInputUsingModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, List<? extends ModelReference<?>> inputs,
                                           TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        super(subject, descriptor, inputs);
        this.action = action;
    }

    public static <T> DirectNodeInputUsingModelAction<T> of(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, List<? extends ModelReference<?>> inputs,
                                                      TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        return new DirectNodeInputUsingModelAction<T>(modelReference, descriptor, inputs, action);
    }

    public static <T> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, List<? extends ModelReference<?>> input, final BiAction<? super MutableModelNode, ? super List<ModelView<?>>> action) {
        return new AbstractModelAction<T>(reference, descriptor, input) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                action.execute(modelNode, inputs);
            }
        };
    }

    public static <T, I> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, ModelReference<I> input, final BiAction<? super MutableModelNode, ? super I> action) {
        return new AbstractModelAction<T>(reference, descriptor, input) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                action.execute(modelNode, Cast.<I>uncheckedCast(inputs.get(0).getInstance()));
            }
        };
    }

    public static <T, I, J> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, ModelReference<I> input1, ModelReference<J> input2, final TriAction<? super MutableModelNode, ? super I, ? super J> action) {
        return new AbstractModelAction<T>(reference, descriptor, input1, input2) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                action.execute(modelNode,
                    Cast.<I>uncheckedCast(inputs.get(0).getInstance()),
                    Cast.<J>uncheckedCast(inputs.get(1).getInstance())
                );
            }
        };
    }

    @Override
    public void execute(MutableModelNode modelNode, T view, List<ModelView<?>> inputs) {
        action.execute(modelNode, view, inputs);
    }
}
