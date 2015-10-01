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
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

public class DirectNodeNoInputsModelAction<T> extends AbstractModelActionWithView<T> {

    private final BiAction<? super MutableModelNode, ? super T> action;

    private DirectNodeNoInputsModelAction(ModelReference<T> subjectReference, ModelRuleDescriptor descriptor, BiAction<? super MutableModelNode, ? super T> action) {
        super(subjectReference, descriptor, Collections.<ModelReference<?>>emptyList());
        this.action = action;
    }

    public static <T> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, final Action<? super MutableModelNode> action) {
        return new AbstractModelAction<T>(reference, descriptor, Collections.<ModelReference<?>>emptyList()) {
            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                action.execute(modelNode);
            }
        };
    }

    public static <T> ModelAction of(ModelReference<T> reference, ModelRuleDescriptor descriptor, BiAction<? super MutableModelNode, ? super T> action) {
        return new DirectNodeNoInputsModelAction<T>(reference, descriptor, action);
    }

    @Override
    public void execute(MutableModelNode modelNode, T view, List<ModelView<?>> inputs) {
        action.execute(modelNode, view);
    }
}
