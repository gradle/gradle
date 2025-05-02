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

package org.gradle.model.internal.fixture;

import org.gradle.api.Action;
import org.gradle.internal.BiAction;
import org.gradle.internal.TriAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class ModelActionBuilder<T> {

    private static final List<ModelReference<?>> NO_REFS = Collections.emptyList();

    private ModelPath path;
    private ModelType<T> type;
    private ModelRuleDescriptor descriptor;

    private ModelActionBuilder(ModelPath path, ModelType<T> type, ModelRuleDescriptor descriptor) {
        this.path = path;
        this.type = type;
        this.descriptor = descriptor;
    }

    public static ModelActionBuilder<Object> of() {
        return new ModelActionBuilder<Object>(null, ModelType.UNTYPED, new SimpleModelRuleDescriptor("testrule"));
    }

    private <N> ModelActionBuilder<N> copy(ModelType<N> type) {
        return new ModelActionBuilder<N>(path, type, descriptor);
    }

    public ModelActionBuilder<T> path(String path) {
        return this.path(ModelPath.path(path));
    }

    public ModelActionBuilder<T> path(ModelPath path) {
        this.path = path;
        return this;
    }

    public ModelActionBuilder<T> descriptor(String descriptor) {
        return descriptor(new SimpleModelRuleDescriptor(descriptor));
    }

    public ModelActionBuilder<T> descriptor(ModelRuleDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    public <N> ModelActionBuilder<N> type(Class<N> type) {
        return type(ModelType.of(type));
    }

    public <N> ModelActionBuilder<N> type(ModelType<N> type) {
        return copy(type);
    }

    public ModelAction action(final Action<? super T> action) {
        return build(NO_REFS, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                action.execute(t);
            }
        });
    }

    public ModelAction node(final Action<? super MutableModelNode> action) {
        return toAction(action, path, type, descriptor);
    }

    public <I> ModelAction action(ModelPath modelPath, Class<I> inputType, BiAction<? super T, ? super I> action) {
        return action(modelPath, ModelType.of(inputType), action);
    }

    public <I> ModelAction action(String modelPath, Class<I> inputType, BiAction<? super T, ? super I> action) {
        return action(ModelPath.path(modelPath), ModelType.of(inputType), action);
    }

    public <I> ModelAction action(ModelPath modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
        return action(modelPath, inputType, inputType.toString(), action);
    }

    public <I> ModelAction action(String modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
        return action(modelPath, inputType, modelPath, action);
    }

    public <I> ModelAction action(final ModelPath modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
        return action(ModelReference.of(modelPath, inputType, referenceDescription), action);
    }

    public <I> ModelAction action(final ModelReference<I> inputReference, final BiAction<? super T, ? super I> action) {
        return build(Collections.<ModelReference<?>>singletonList(inputReference), new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                action.execute(t, ModelViews.assertType(inputs.get(0), inputReference.getType()).getInstance());
            }
        });
    }

    public <I> ModelAction action(final String modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
        return action(ModelPath.path(modelPath), inputType, referenceDescription, action);
    }

    public <I> ModelAction action(final ModelType<I> inputType, final BiAction<? super T, ? super I> action) {
        return action((ModelPath) null, inputType, action);
    }

    public <I> ModelAction action(final Class<I> inputType, final BiAction<? super T, ? super I> action) {
        return action(ModelType.of(inputType), action);
    }

    private ModelAction build(List<ModelReference<?>> references, TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
        return toAction(references, action, path, type, descriptor);
    }

    private static <T> ModelAction toAction(final List<ModelReference<?>> references, final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
        return DirectNodeInputUsingModelAction.of(subject(path, type), descriptor, references, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode modelNode, T t, List<ModelView<?>> inputs) {
                action.execute(modelNode, t, inputs);
            }
        });
    }

    private static <T> ModelAction toAction(Action<? super MutableModelNode> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
        return DirectNodeNoInputsModelAction.of(subject(path, type), descriptor, action);
    }

    private static <T> ModelReference<T> subject(ModelPath path, ModelType<T> type) {
        return path != null ? ModelReference.of(path, type) : ModelReference.of(type).inScope(ModelPath.ROOT);
    }
}
