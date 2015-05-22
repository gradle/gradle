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

package org.gradle.model.internal.registry;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;

import java.util.Collection;
import java.util.Collections;

class RuleBindings {
    private final NodeIndex rulesBySubject;
    private final NodeIndex rulesByInput;

    public RuleBindings(ModelGraph graph) {
        rulesBySubject = new NodeIndex(graph);
        rulesByInput = new NodeIndex(graph);
    }

    public void add(ModelNodeInternal node) {
        rulesBySubject.nodeAdded(node);
        rulesByInput.nodeAdded(node);
    }

    public void remove(ModelNodeInternal node) {
        rulesBySubject.nodeRemoved(node);
        rulesByInput.nodeRemoved(node);
    }

    public void add(RuleBinder ruleBinder) {
        rulesBySubject.put(subject(ruleBinder), ruleBinder);
        for (ModelBinding binding : ruleBinder.getInputBindings()) {
            rulesByInput.put(binding, ruleBinder);
        }
    }

    private ModelBinding subject(RuleBinder ruleBinder) {
        if (ruleBinder.getSubjectBinding() != null) {
            return ruleBinder.getSubjectBinding();
        }
        // Create a dummy binding. Could probably reorganise things to avoid this
        return new ModelBinding(ruleBinder.getDescriptor(), ruleBinder.getSubjectReference(), true) {
            @Override
            public void onCreate(ModelNodeInternal node) {
            }
        };
    }

    /**
     * Returns the set of rules with the given target as their subject.
     */
    public Collection<RuleBinder> getRulesWithSubject(NodeAtState target) {
        return rulesBySubject.get(target);
    }

    /**
     * Returns the set of rules with the given target with no state as their subject.
     */
    public Collection<RuleBinder> getRulesWithSubject(ModelPath target) {
        return rulesBySubject.get(target);
    }

    /**
     * Returns the set of rules with the given input.
     */
    public Collection<RuleBinder> getRulesWithInput(NodeAtState input) {
        return rulesByInput.get(input);
    }

    private static class Reference {
        final ModelBinding binding;
        final RuleBinder owner;

        public Reference(RuleBinder owner, ModelBinding binding) {
            this.owner = owner;
            this.binding = binding;
        }
    }

    private static class NodeIndex {
        private final ModelGraph modelGraph;
        private final Multimap<ModelPath, Reference> rulesByPath = ArrayListMultimap.create();
        private final Multimap<ModelPath, Reference> rulesByScope = ArrayListMultimap.create();
        private final Multimap<NodeAtState, RuleBinder> boundAtState = LinkedHashMultimap.create();
        private final Multimap<ModelPath, RuleBinder> boundNoState = LinkedHashMultimap.create();

        public NodeIndex(ModelGraph modelGraph) {
            this.modelGraph = modelGraph;
        }

        public void nodeAdded(ModelNodeInternal node) {
            Collection<Reference> references = rulesByPath.get(node.getPath());
            for (Reference reference : references) {
                bound(reference.binding, reference.owner, node);
            }
            references = rulesByScope.get(node.getPath());
            addTypeMatches(node, references);
            references = rulesByScope.get(node.getPath().getParent());
            addTypeMatches(node, references);
        }

        private void addTypeMatches(ModelNodeInternal node, Collection<Reference> references) {
            for (Reference reference : references) {
                if (reference.binding.isTypeCompatible(node.getPromise())) {
                    bound(reference.binding, reference.owner, node);
                }
            }
        }

        public void nodeRemoved(ModelNodeInternal node) {
            // This could be more efficient; assume that removal happens much less often than addition
            for (ModelNode.State state : ModelNode.State.values()) {
                for (RuleBinder rule : boundAtState.removeAll(new NodeAtState(node.getPath(), state))) {
                    if (rule.getSubjectBinding() != null) {
                        rule.getSubjectBinding().onRemove(node);
                    }
                    for (ModelBinding binding : rule.getInputBindings()) {
                        binding.onRemove(node);
                    }
                }
            }
        }

        void put(ModelBinding binding, RuleBinder rule) {
            BindingPredicate predicate = binding.getPredicate();
            if (predicate.getPath() != null) {
                if (predicate.getScope() != null) {
                    throw new UnsupportedOperationException("Currently not implemented");
                }
                ModelNodeInternal node = modelGraph.find(predicate.getPath());
                if (node != null) {
                    bound(binding, rule, node);
                }
                // Need to continue to watch to deal with node removal
                rulesByPath.put(predicate.getPath(), new Reference(rule, binding));
            } else if (predicate.getScope() != null) {
                for (ModelNodeInternal node : modelGraph.findAllInScope(predicate.getScope())) {
                    if (binding.isTypeCompatible(node.getPromise())) {
                        bound(binding, rule, node);
                    }
                }
                // Need to continue to watch for potential later matches, which will make the binding ambiguous, and node removal
                rulesByScope.put(predicate.getScope(), new Reference(rule, binding));
            } else {
                throw new UnsupportedOperationException("Currently not implemented");
            }
        }

        private void bound(ModelBinding binding, RuleBinder rule, ModelNodeInternal node) {
            binding.onCreate(node);
            if (binding.predicate.getState() != null) {
                boundAtState.put(new NodeAtState(node.getPath(), binding.predicate.getState()), rule);
            } else {
                boundNoState.put(node.getPath(), rule);
            }
        }

        /**
         * Returns rules for given target and no target state.
         */
        public Collection<RuleBinder> get(ModelPath path) {
            Collection<RuleBinder> result = boundNoState.get(path);
            return result == null ? Collections.<RuleBinder>emptyList() : result;
        }

        /**
         * Returns rules for given target at state.
         */
        public Collection<RuleBinder> get(NodeAtState nodeAtState) {
            Collection<RuleBinder> result = boundAtState.get(nodeAtState);
            return result == null ? Collections.<RuleBinder>emptyList() : result;
        }
    }
}
