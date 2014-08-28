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

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.model.internal.core.ModelBinding;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The progressive binding of the subject/inputs of the references of a model rule.
 *
 * This type is mutable.
 */
public class RuleBinder<T> {

    private final ModelReference<T> subjectReference;
    private final List<ModelReference<?>> inputReferences;

    private final ModelRuleDescriptor descriptor;

    private Action<? super RuleBinder<T>> onBind;

    private int inputsBound;

    private ModelBinding<T> subjectBinding;
    private List<ModelBinding<?>> inputBindings;

    public RuleBinder(@Nullable ModelReference<T> subjectReference, List<ModelReference<?>> inputReferences, ModelRuleDescriptor descriptor, Action<? super RuleBinder<T>> onBind) {
        this.subjectReference = subjectReference;
        this.inputReferences = inputReferences;
        this.descriptor = descriptor;
        this.onBind = onBind;

        this.inputBindings = inputReferences.isEmpty() ? Collections.<ModelBinding<?>>emptyList() : Arrays.asList(new ModelBinding<?>[inputReferences.size()]); // fix size

        maybeFire();
    }

    @Nullable
    public ModelReference<T> getSubjectReference() {
        return subjectReference;
    }

    public List<ModelReference<?>> getInputReferences() {
        return inputReferences;
    }

    public ModelBinding<T> getSubjectBinding() {
        return subjectBinding;
    }

    public List<ModelBinding<?>> getInputBindings() {
        return inputBindings;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public boolean bindSubject(ModelPath path) {
        assert this.subjectBinding == null;
        this.subjectBinding = bind(subjectReference, path);
        return maybeFire();
    }

    public boolean bindInput(int i, ModelPath path) {
        assert this.inputBindings.get(i) == null;
        this.inputBindings.set(i, bind(inputReferences.get(i), path));
        inputsBound += 1;
        return maybeFire();
    }

    private boolean maybeFire() {
        if (isBound()) {
            fire();
            return true;
        } else {
            return false;
        }
    }

    public boolean isBound() {
        return (subjectReference == null || subjectBinding != null) && inputsBound == inputReferences.size();
    }

    private void fire() {
        onBind.execute(this);
        onBind = null; // let go for gc
    }

    private static <I> ModelBinding<I> bind(ModelReference<I> reference, ModelPath path) {
        return ModelBinding.of(reference, path);
    }

    private String pathStringOrNull(ModelReference<?> reference) {
        return reference.getPath() == null ? null : reference.getPath().toString();
    }

}
