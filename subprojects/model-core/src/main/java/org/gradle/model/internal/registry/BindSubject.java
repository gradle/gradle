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

package org.gradle.model.internal.registry;

import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;

class BindSubject<T> implements Action<ModelNodeInternal> {
    private final RuleBinder<T> binder;
    private final Multimap<ModelPath, RuleBinder<?>> mutationBinders;
    private final ModelActionRole type;
    private final ModelAction<T> mutator;

    public BindSubject(RuleBinder<T> binder, ModelAction<T> mutator, ModelActionRole type, Multimap<ModelPath, RuleBinder<?>> mutationBinders) {
        this.binder = binder;
        this.mutator = mutator;
        this.type = type;
        this.mutationBinders = mutationBinders;
    }

    public void execute(ModelNodeInternal subject) {
        if (!subject.canApply(type)) {
            throw new IllegalStateException(String.format(
                    "Cannot add %s rule '%s' for model element '%s' when element is in state %s.",
                    type,
                    mutator.getDescriptor(),
                    subject.getPath(),
                    subject.getState()
            ));
        }
        mutationBinders.put(subject.getPath(), binder);
        binder.bindSubject(subject);
    }
}
