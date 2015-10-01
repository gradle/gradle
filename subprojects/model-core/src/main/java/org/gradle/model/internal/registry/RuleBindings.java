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
import java.util.Iterator;

class RuleBindings {
    private final ModelGraph modelGraph;
    private final NodeIndex rulesBySubject;
    private final NodeIndex rulesByInput;
    private final Multimap<ModelPath, Reference> pathReferences = ArrayListMultimap.create();
    private final Multimap<ModelPath, Reference> scopeReferences = ArrayListMultimap.create();

    public RuleBindings(ModelGraph graph) {
        this.modelGraph = graph;
        rulesBySubject = new NodeIndex("rulesBySubject");
        rulesByInput = new NodeIndex("rulesByInput");
    }

    public void nodeCreated(ModelNodeInternal node) {
        for (Reference reference : pathReferences.get(node.getPath())) {
            if (reference.binding.canBindInState(node.getState())) {
                bound(reference, node);
            }
        }
    }

    public void nodeProjectionsDefined(ModelNodeInternal node) {
        for (Reference reference : pathReferences.get(node.getPath())) {
            if (!reference.binding.isBound()) {
                bound(reference, node);
            }
        }
        addTypeMatches(node, scopeReferences.get(node.getPath()));
        addTypeMatches(node, scopeReferences.get(node.getPath().getParent()));
    }

    private void addTypeMatches(ModelNodeInternal node, Collection<Reference> references) {
        for (Reference reference : references) {
            if (reference.binding.isTypeCompatible(node.getPromise())) {
                bound(reference, node);
            }
        }
    }

    private void bound(Reference reference, ModelNodeInternal node) {
        ModelBinding binding = reference.binding;
        binding.onCreate(node);
        if (binding.predicate.getState() == null) {
            throw new IllegalArgumentException("No state specified for binding: " + binding);
        }
        reference.index.put(new NodeAtState(node.getPath(), binding.predicate.getState()), reference.owner);
    }

    public void remove(ModelNodeInternal node) {
        rulesBySubject.nodeRemoved(node);
        rulesByInput.nodeRemoved(node);
    }

    public void remove(ModelNodeInternal node, RuleBinder ruleBinder) {
        rulesBySubject.remove(node, ruleBinder);
        rulesByInput.remove(node, ruleBinder);
        removeReferences(node, ruleBinder, pathReferences);
        removeReferences(node, ruleBinder, scopeReferences);
    }

    private void removeReferences(ModelNodeInternal node, RuleBinder ruleBinder, Multimap<ModelPath, Reference> references) {
        Iterator<Reference> iterator = references.get(node.getPath()).iterator();
        while (iterator.hasNext()) {
            Reference reference = iterator.next();
            if (reference.owner.equals(ruleBinder)) {
                iterator.remove();
            }
        }
    }

    public void add(RuleBinder ruleBinder) {
        addRule(ruleBinder, rulesBySubject, subject(ruleBinder));
        for (ModelBinding binding : ruleBinder.getInputBindings()) {
            addRule(ruleBinder, rulesByInput, binding);
        }
    }

    private void addRule(RuleBinder rule, NodeIndex index, ModelBinding binding) {
        Reference reference = new Reference(rule, index, binding);
        BindingPredicate predicate = binding.getPredicate();
        if (predicate.getPath() != null) {
            if (predicate.getScope() != null) {
                throw new UnsupportedOperationException("Currently not implemented");
            }
            ModelNodeInternal node = modelGraph.find(predicate.getPath());
            if (node != null && reference.binding.canBindInState(node.getState())) {
                bound(reference, node);
            }
            // Need to continue to watch to deal with node removal
            pathReferences.put(predicate.getPath(), reference);
        } else if (predicate.getScope() != null) {
            for (ModelNodeInternal node : modelGraph.findAllInScope(predicate.getScope())) {
                // Do not try to attach to nodes that are not in ProjectionsDefined yet
                if (!node.isAtLeast(ModelNode.State.ProjectionsDefined)) {
                    continue;
                }
                if (binding.isTypeCompatible(node.getPromise())) {
                    bound(reference, node);
                }
            }
            // Need to continue to watch for potential later matches, which will make the binding ambiguous, and node removal
            scopeReferences.put(predicate.getScope(), reference);
        } else {
            throw new UnsupportedOperationException("Currently not implemented");
        }
    }

    private ModelBinding subject(RuleBinder ruleBinder) {
        if (ruleBinder.getSubjectBinding() != null) {
            return ruleBinder.getSubjectBinding();
        }
        // Create a dummy binding. Could probably reorganise things to avoid this
        return new ModelBinding(ruleBinder.getDescriptor(), ruleBinder.getSubjectReference(), true) {
            @Override
            public boolean canBindInState(ModelNode.State state) {
                return true;
            }

            @Override
            public void onCreate(ModelNodeInternal node) {
            }
        };
    }

    private static void unbind(RuleBinder rule, ModelNodeInternal node) {
        if (rule.getSubjectBinding() != null) {
            rule.getSubjectBinding().onRemove(node);
        }
        for (ModelBinding binding : rule.getInputBindings()) {
            binding.onRemove(node);
        }
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
        final NodeIndex index;
        final RuleBinder owner;

        public Reference(RuleBinder owner, NodeIndex index, ModelBinding binding) {
            this.owner = owner;
            this.index = index;
            this.binding = binding;
        }

        @Override
        public String toString() {
            return binding + " in " + index.name;
        }
    }

    private static class NodeIndex {
        private final Multimap<NodeAtState, RuleBinder> boundAtState = LinkedHashMultimap.create();

        private final String name;

        private NodeIndex(String name) {
            this.name = name;
        }

        public void nodeRemoved(ModelNodeInternal node) {
            // This could be more efficient; assume that removal happens much less often than addition
            for (ModelNode.State state : ModelNode.State.values()) {
                for (RuleBinder rule : boundAtState.removeAll(new NodeAtState(node.getPath(), state))) {
                    unbind(rule, node);
                }
            }
        }

        public void put(NodeAtState nodeAtState, RuleBinder binder) {
            boundAtState.put(nodeAtState, binder);
        }

        /**
         * Returns rules for given target at state.
         */
        public Collection<RuleBinder> get(NodeAtState nodeAtState) {
            Collection<RuleBinder> result = boundAtState.get(nodeAtState);
            return result == null ? Collections.<RuleBinder>emptyList() : result;
        }

        public void remove(ModelNodeInternal node, RuleBinder ruleBinder) {
            unbind(ruleBinder, node);
            boundAtState.values().remove(ruleBinder);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
