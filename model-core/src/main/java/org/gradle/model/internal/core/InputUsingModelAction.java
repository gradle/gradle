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

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

public class InputUsingModelAction<T> extends AbstractModelActionWithView<T> {
    private final BiAction<? super T, ? super List<ModelView<?>>> action;

    public InputUsingModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, BiAction<? super T, ? super List<ModelView<?>>> action) {
        super(subject, descriptor, inputs);
        this.action = action;
    }

    public static <T> InputUsingModelAction<T> of(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, BiAction<? super T, ? super List<ModelView<?>>> initializer) {
        return new InputUsingModelAction<T>(modelReference, descriptor, inputs, initializer);
    }

    public static <T, I> InputUsingModelAction<T> single(ModelReference<T> modelReference, ModelRuleDescriptor descriptor, final ModelReference<I> input, final BiAction<? super T, ? super I> initializer) {
        return new InputUsingModelAction<T>(modelReference, descriptor, Collections.<ModelReference<?>>singletonList(input), new BiAction<T, List<ModelView<?>>>() {
            @Override
            public void execute(T t, List<ModelView<?>> modelViews) {
                initializer.execute(t, ModelViews.assertType(modelViews.get(0), input.getType()).getInstance());
            }
        });
    }

    @Override
    protected void execute(MutableModelNode modelNode, T view, List<ModelView<?>> inputs) {
        action.execute(view, inputs);
    }
}
