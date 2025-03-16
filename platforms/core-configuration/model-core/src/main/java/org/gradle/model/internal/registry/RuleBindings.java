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

import com.google.common.collect.Maps;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class RuleBindings {
    private final NodeAtStateIndex rulesBySubject;
    private final NodeAtStateIndex rulesByInput;
    private final PathPredicateIndex untypedPathReferences = new PathPredicateIndex();
    private final PathPredicateIndex typedPathReferences = new PathPredicateIndex();
    private final TypePredicateIndex scopeReferences = new TypePredicateIndex();

    public RuleBindings() {
        rulesBySubject = new NodeAtStateIndex("rulesBySubject");
        rulesByInput = new NodeAtStateIndex("rulesByInput");
    }

    public void nodeCreated(ModelNodeInternal node) {
        untypedPathReferences.addNode(node);
    }

    public void nodeDiscovered(ModelNodeInternal node) {
        typedPathReferences.addNode(node);
        scopeReferences.addNodeToScope(node.getPath(), node);
        scopeReferences.addNodeToScope(node.getPath().getParent(), node);
    }

    private void bound(Reference reference, ModelNodeInternal node) {
        ModelBinding binding = reference.binding;
        binding.onBind(node);
        reference.index.put(new NodeAtState(node.getPath(), binding.predicate.getState()), reference.owner);
    }

    public void remove(ModelNodeInternal node) {
        untypedPathReferences.removeNode(node);
        typedPathReferences.removeNode(node);
        scopeReferences.removeNodeFromScope(node.getPath(), node);
        scopeReferences.removeNodeFromScope(node.getPath().getParent(), node);
        rulesBySubject.nodeRemoved(node);
        rulesByInput.nodeRemoved(node);
    }

    public void add(RuleBinder ruleBinder) {
        addRule(ruleBinder, rulesBySubject, ruleBinder.getSubjectBinding());
        for (ModelBinding binding : ruleBinder.getInputBindings()) {
            addRule(ruleBinder, rulesByInput, binding);
        }
    }

    private void addRule(RuleBinder rule, NodeAtStateIndex index, ModelBinding binding) {
        Reference reference = new Reference(rule, index, binding);
        BindingPredicate predicate = binding.getPredicate();
        if (predicate.getPath() != null) {
            if (predicate.getScope() != null) {
                throw new UnsupportedOperationException("Currently not implemented");
            }
            if (reference.binding.canBindInState(ModelNode.State.Registered)) {
                untypedPathReferences.addReference(reference);
            } else {
                typedPathReferences.addReference(reference);
            }
        } else if (predicate.getScope() != null) {
            scopeReferences.addReference(reference);
        } else {
            throw new UnsupportedOperationException("Currently not implemented");
        }
    }

    private static void unbind(RuleBinder rule, ModelNodeInternal node) {
        rule.getSubjectBinding().onUnbind(node);
        for (ModelBinding binding : rule.getInputBindings()) {
            binding.onUnbind(node);
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
        final NodeAtStateIndex index;
        final RuleBinder owner;

        public Reference(RuleBinder owner, NodeAtStateIndex index, ModelBinding binding) {
            this.owner = owner;
            this.index = index;
            this.binding = binding;
        }

        @Override
        public String toString() {
            return binding + " in " + index.name;
        }
    }

    private class PredicateMatches {
        final List<Reference> references = new ArrayList<Reference>();
        ModelNodeInternal match;

        void match(ModelNodeInternal node) {
            for (Reference reference : references) {
                bound(reference, node);
            }
            match = node;
        }

        void add(Reference reference) {
            references.add(reference);
            if (match != null) {
                bound(reference, match);
            }
        }

        public void remove() {
            match = null;
        }
    }

    private class PathPredicateIndex {
        final Map<ModelPath, PredicateMatches> predicates = new LinkedHashMap<>();

        public void addNode(ModelNodeInternal node) {
            predicatesForPath(node.getPath()).match(node);
        }

        public void addReference(Reference reference) {
            ModelPath path = reference.binding.getPredicate().getPath();
            predicatesForPath(path).add(reference);
        }

        private PredicateMatches predicatesForPath(ModelPath path) {
            PredicateMatches predicatesForReference = predicates.get(path);
            if (predicatesForReference == null) {
                predicatesForReference = new PredicateMatches();
                predicates.put(path, predicatesForReference);
            }
            return predicatesForReference;
        }

        public void removeNode(ModelNodeInternal node) {
            predicatesForPath(node.getPath()).remove();
        }
    }

    private class ScopeIndex {
        final Map<ModelType<?>, PredicateMatches> types = new LinkedHashMap<>();
        final List<ModelNodeInternal> nodes = new ArrayList<>();

        public void addNode(ModelNodeInternal node) {
            nodes.add(node);
            for (Map.Entry<ModelType<?>, PredicateMatches> entry : types.entrySet()) {
                if (node.canBeViewedAs(entry.getKey())) {
                    entry.getValue().match(node);
                }
            }
        }

        public void removeNode(ModelNodeInternal node) {
            nodes.remove(node);
            for (PredicateMatches matches : types.values()) {
                if (matches.match == node) {
                    matches.remove();
                }
            }
        }

        public void addReference(Reference reference) {
            ModelType<?> type = reference.binding.getPredicate().getType();
            PredicateMatches predicateMatches = types.get(type);
            boolean newType = predicateMatches == null;
            if (predicateMatches == null) {
                predicateMatches = new PredicateMatches();
                types.put(type, predicateMatches);
            }
            predicateMatches.add(reference);
            if (newType) {
                for (ModelNodeInternal node : nodes) {
                    if (node.canBeViewedAs(type)) {
                        predicateMatches.match(node);
                    }
                }
            }
        }
    }

    private class TypePredicateIndex {
        final Map<ModelPath, ScopeIndex> scopes = new LinkedHashMap<>();

        public void addNodeToScope(ModelPath path, ModelNodeInternal node) {
            scopeForPath(path).addNode(node);
        }

        public void removeNodeFromScope(ModelPath path, ModelNodeInternal node) {
            scopeForPath(path).removeNode(node);
        }

        public void addReference(Reference reference) {
            ModelPath path = reference.binding.getPredicate().getScope();
            scopeForPath(path).addReference(reference);
        }

        private ScopeIndex scopeForPath(ModelPath path) {
            ScopeIndex scope = scopes.get(path);
            if (scope == null) {
                scope = new ScopeIndex();
                scopes.put(path, scope);
            }
            return scope;
        }
    }

    private static class NodeAtStateIndex {
        private final EnumMap<ModelNode.State, Map<String, List<RuleBinder>>> boundAtState = Maps.newEnumMap(ModelNode.State.class);

        private final String name;

        private NodeAtStateIndex(String name) {
            this.name = name;
        }

        private Map<String, List<RuleBinder>> getByState(ModelNode.State state) {
            Map<String, List<RuleBinder>> map = boundAtState.get(state);
            if (map == null) {
                map = new HashMap<String, List<RuleBinder>>(64);
                boundAtState.put(state, map);
            }
            return map;
        }

        public void nodeRemoved(ModelNodeInternal node) {
            // This could be more efficient; assume that removal happens much less often than addition
            for (ModelNode.State state : ModelNode.State.values()) {
                Map<String, List<RuleBinder>> byState = getByState(state);
                List<RuleBinder> remove = byState.remove(node.getPath().toString());
                if (remove != null) {
                    for (RuleBinder rule : remove) {
                        unbind(rule, node);
                    }
                }
            }
        }

        public void put(NodeAtState nodeAtState, RuleBinder binder) {
            Map<String, List<RuleBinder>> byState = getByState(nodeAtState.state);
            String path = nodeAtState.path.toString();
            List<RuleBinder> byPath = getByPath(byState, path);
            if (!byPath.contains(binder)) {
                byPath.add(binder);
            }
        }

        private List<RuleBinder> getByPath(Map<String, List<RuleBinder>> byState, String path) {
            List<RuleBinder> ruleBinders = byState.get(path);
            if (ruleBinders == null) {
                ruleBinders = new ArrayList<>();
                byState.put(path, ruleBinders);
            }
            return ruleBinders;
        }

        /**
         * Returns rules for given target at state.
         */
        public Collection<RuleBinder> get(NodeAtState nodeAtState) {
            return getByPath(getByState(nodeAtState.state), nodeAtState.path.toString());
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
