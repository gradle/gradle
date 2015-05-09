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
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;

import java.util.Collection;
import java.util.Collections;

class RuleBindings {
    private final ModelGraph graph;
    private final NodeIndex rulesBySubject;
    private final NodeIndex rulesByInput;

    public RuleBindings(ModelGraph graph) {
        this.graph = graph;
        rulesBySubject = new NodeIndex(graph);
        rulesByInput = new NodeIndex(graph);
        graph.addListener(new ModelCreationListener() {
            @Override
            public boolean onCreate(ModelNodeInternal node) {
                rulesBySubject.nodeAdded(node);
                rulesByInput.nodeAdded(node);
                return false;
            }
        });
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
            public boolean onCreate(ModelNodeInternal node) {
                return false;
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
        private final Multimap<NodeAtState, RuleBinder> bound = LinkedHashMultimap.create();

        public NodeIndex(ModelGraph modelGraph) {
            this.modelGraph = modelGraph;
        }

        public void nodeAdded(ModelNodeInternal node) {
            Collection<Reference> references = rulesByPath.removeAll(node.getPath());
            for (Reference reference : references) {
                reference.binding.onCreate(node);
                bound.put(new NodeAtState(node.getPath(), reference.binding.reference.getState()), reference.owner);
            }
            references = rulesByScope.get(node.getPath());
            checkTypeMatches(node, references);
            references = rulesByScope.get(node.getPath().getParent());
            checkTypeMatches(node, references);
        }

        private void checkTypeMatches(ModelNodeInternal node, Collection<Reference> references) {
            for (Reference reference : references) {
                if (reference.binding.isTypeCompatible(node.getPromise())) {
                    reference.binding.onCreate(node);
                    bound.put(new NodeAtState(node.getPath(), reference.binding.reference.getState()), reference.owner);
                }
            }
        }

        void put(ModelBinding binding, RuleBinder rule) {
            ModelReference<?> reference = binding.getReference();
            if (reference.getPath() != null) {
                if (reference.getParent() != null || reference.getScope() != null) {
                    throw new UnsupportedOperationException("Currently not implemented");
                }
                ModelNodeInternal node = modelGraph.find(reference.getPath());
                if (node != null) {
                    binding.onCreate(node);
                    bound.put(new NodeAtState(node.getPath(), reference.getState()), rule);
                } else {
                    rulesByPath.put(reference.getPath(), new Reference(rule, binding));
                }
            } else if (reference.getScope() != null) {
                if (reference.getParent() != null) {
                    throw new UnsupportedOperationException("Currently not implemented");
                }
                for (ModelNodeInternal node : modelGraph.findAllInScope(reference.getScope())) {
                    if (binding.isTypeCompatible(node.getPromise())) {
                        binding.onCreate(node);
                        bound.put(new NodeAtState(node.getPath(), reference.getState()), rule);
                    }
                }
                // Need to continue to watch for potential later matches, which will make the binding ambiguous
                rulesByScope.put(reference.getScope(), new Reference(rule, binding));
            } else {
                throw new UnsupportedOperationException("Currently not implemented");
            }
        }

        public Collection<RuleBinder> get(NodeAtState nodeAtState) {
            Collection<RuleBinder> result = bound.get(nodeAtState);
            return result == null ? Collections.<RuleBinder>emptyList() : result;
        }
    }
}
