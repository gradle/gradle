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
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

public class NoInputsModelAction<T> extends AbstractModelActionWithView<T> {
    private final Action<? super T> configAction;

    public NoInputsModelAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, Action<? super T> configAction) {
        super(subject, descriptor, Collections.<ModelReference<?>>emptyList());
        this.configAction = configAction;
    }

    public static <T> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, Action<? super T> configAction) {
        return new NoInputsModelAction<T>(reference, descriptor, configAction);
    }

    @Override
    public void execute(MutableModelNode modelNode, T view, List<ModelView<?>> inputs) {
        configAction.execute(view);
    }
}
