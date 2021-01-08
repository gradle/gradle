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

package org.gradle.platform.base.internal.registry;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.inspect.AbstractMethodRuleAction;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.ModelRuleInvoker;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.List;

public abstract class ModelMapBasedRule<T, C> extends AbstractMethodRuleAction<C> {
    private final List<ModelReference<?>> inputs;
    protected final int baseTypeParameterIndex;

    public ModelMapBasedRule(ModelReference<C> subject, final ModelType<? extends T> baseType, MethodRuleDefinition<?, ?> ruleDefinition, ModelReference<?>... additionalInputs) {
        super(subject, ruleDefinition.getDescriptor());
        this.inputs = calculateInputs(
                baseType,
                ruleDefinition.getReferences().subList(1, ruleDefinition.getReferences().size()),
                Arrays.asList(additionalInputs)
        );
        this.baseTypeParameterIndex = 1 + Iterables.indexOf(ruleDefinition.getReferences().subList(1, ruleDefinition.getReferences().size()), new Predicate<ModelReference<?>>() {
            @Override
            public boolean apply(ModelReference<?> element) {
                return element.getType().equals(baseType);
            }
        });
    }

    private static ImmutableList<ModelReference<?>> calculateInputs(final ModelType<?> baseType, final List<ModelReference<?>> references, List<ModelReference<?>> modelReferences) {
        Iterable<ModelReference<?>> filteredReferences = Iterables.filter(references, new Predicate<ModelReference<?>>() {
            @Override
            public boolean apply(ModelReference<?> element) {
                return !element.getType().equals(baseType);
            }
        });

        ImmutableList.Builder<ModelReference<?>> allInputs = ImmutableList.builder();
        allInputs.addAll(modelReferences);
        allInputs.addAll(filteredReferences);
        return allInputs.build();
    }

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return inputs;
    }

    protected void invoke(ModelRuleInvoker<?> ruleInvoker, List<ModelView<?>> inputs, ModelMap<?> modelMap, T baseTypeParameter, Object... ignoredInputs) {
        List<Object> ignoredInputsList = Arrays.asList(ignoredInputs);
        Object[] args = new Object[inputs.size() + 2 - ignoredInputs.length];
        args[0] = modelMap;
        args[baseTypeParameterIndex] = baseTypeParameter;

        for (ModelView<?> view : inputs) {
            Object instance = view.getInstance();
            if (ignoredInputsList.contains(instance)) {
                continue;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    args[i] = instance;
                    break;
                }
            }
        }
        ruleInvoker.invoke(args);
    }
}
