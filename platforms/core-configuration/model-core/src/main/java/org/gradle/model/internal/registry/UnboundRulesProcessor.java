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

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Transformer;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.report.unbound.UnboundRule;
import org.gradle.model.internal.report.unbound.UnboundRuleInput;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ThreadSafe
class UnboundRulesProcessor {

    private final Iterable<? extends RuleBinder> binders;
    private final Transformer<? extends Collection<? extends ModelPath>, ? super ModelPath> suggestionsProvider;

    public UnboundRulesProcessor(Iterable<? extends RuleBinder> binders, Transformer<? extends Collection<? extends ModelPath>, ? super ModelPath> suggestionsProvider) {
        this.binders = binders;
        this.suggestionsProvider = suggestionsProvider;
    }

    public List<? extends UnboundRule> process() {
        List<UnboundRule> unboundRules = new ArrayList<UnboundRule>();
        for (RuleBinder binder : binders) {
            UnboundRule.Builder builder = UnboundRule.descriptor(String.valueOf(binder.getDescriptor()));

            ModelBinding subjectBinding = binder.getSubjectBinding();
            // Only report subject binding if target state is after node creation
            if (subjectBinding.getPredicate().getState().compareTo(ModelNode.State.Created) > 0) {
                UnboundRuleInput.Builder inputBuilder = toInputBuilder(subjectBinding);
                builder.mutableInput(inputBuilder);
            }

            for (int i = 0; i < binder.getInputBindings().size(); ++i) {
                ModelBinding binding = binder.getInputBindings().get(i);
                builder.immutableInput(toInputBuilder(binding));
            }

            unboundRules.add(builder.build());
        }
        return unboundRules;
    }

    private UnboundRuleInput.Builder toInputBuilder(ModelBinding binding) {
        ModelReference<?> reference = binding.getPredicate().getReference();
        UnboundRuleInput.Builder builder = UnboundRuleInput.type(reference.getType());
        ModelPath path;
        if (binding.isBound()) {
            builder.bound();
            path = binding.getNode().getPath();
        } else {
            path = reference.getPath();
            if (path != null) {
                builder.suggestions(CollectionUtils.stringize(suggestionsProvider.transform(path)));
            }
            ModelPath scope = reference.getScope();
            if (scope != null && !scope.equals(ModelPath.ROOT)) {
                builder.scope(scope.toString());
            }
        }
        if (path != null) {
            builder.path(path);
        }
        builder.description(reference.getDescription());
        return builder;
    }
}
