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

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.ConfigurationCycleException;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.report.unbound.UnboundRule;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.gradle.model.internal.core.ModelNode.State.*;

@NotThreadSafe
public class DefaultModelRegistry implements ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelRegistry.class);

    private final ModelGraph modelGraph;
    private final RuleBindings ruleBindings;
    private final ModelRuleExtractor ruleExtractor;
    private final Set<RuleBinder> unboundRules = Sets.newIdentityHashSet();

    private boolean reset;
    private boolean replace;

    public DefaultModelRegistry(ModelRuleExtractor ruleExtractor) {
        this.ruleExtractor = ruleExtractor;
        ModelRegistration rootRegistration = ModelRegistrations.of(ModelPath.ROOT).descriptor("<root>").withProjection(EmptyModelProjection.INSTANCE).build();
        modelGraph = new ModelGraph(new ModelElementNode(rootRegistration, null));
        modelGraph.getRoot().setState(Created);
        ruleBindings = new RuleBindings(modelGraph);
    }

    private static String describe(ModelRuleDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    public DefaultModelRegistry register(ModelRegistration registration) {
        ModelPath path = registration.getPath();
        if (!ModelPath.ROOT.isDirectChild(path)) {
            throw new InvalidModelRuleDeclarationException(registration.getDescriptor(), "Cannot register element at '" + path + "', only top level is allowed (e.g. '" + path.getRootParent() + "')");
        }

        ModelNodeInternal root = modelGraph.getRoot();
        root.addLink(registration);
        return this;
    }

    private ModelNodeInternal registerNode(ModelNodeInternal node) {
        if (reset) {
            unboundRules.removeAll(node.getInitializerRuleBinders());
            return node;
        }

        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);

        addRuleBindings(node);
        modelGraph.add(node);
        ruleBindings.nodeCreated(node);

        ModelRegistration registration = node.getRegistration();
        node.setHidden(registration.isHidden());
        if (registration.isService()) {
            node.ensureAtLeast(Discovered);
        }

        return node;
    }

    private void addRuleBindings(ModelNodeInternal node) {
        for(Map.Entry<ModelActionRole, ? extends ModelAction> entry : node.getRegistration().getActions().entries()) {
            ModelActionRole role = entry.getKey();
            ModelAction action = entry.getValue();
            checkNodePath(node, action);
            // We need to re-bind early actions like projections and creators even when reusing
            boolean earlyAction = role.compareTo(ModelActionRole.Create) <= 0;
            if (!reset || earlyAction) {
                RuleBinder binder = forceBind(action.getSubject(), role, action, ModelPath.ROOT);
                if (earlyAction) {
                    node.addInitializerRuleBinder(binder);
                }
            }
        }
    }

    @Override
    public DefaultModelRegistry configure(ModelActionRole role, ModelAction action) {
        bind(action.getSubject(), role, action, ModelPath.ROOT);
        return this;
    }

    @Override
    public ModelRegistry configure(ModelActionRole role, ModelAction action, ModelPath scope) {
        bind(action.getSubject(), role, action, scope);
        return this;
    }

    @Override
    public ModelRegistry apply(Class<? extends RuleSource> rules) {
        modelGraph.getRoot().applyToSelf(rules);
        return this;
    }

    private static void checkNodePath(ModelNodeInternal node, ModelAction action) {
        if (!node.getPath().equals(action.getSubject().getPath())) {
            throw new IllegalArgumentException(String.format("Element action reference has path (%s) which does not reference this node (%s).", action.getSubject().getPath(), node.getPath()));
        }
    }

    private <T> void bind(ModelReference<T> subject, ModelActionRole role, ModelAction mutator, ModelPath scope) {
        if (reset) {
            return;
        }
        forceBind(subject, role, mutator, scope);
    }

    private <T> RuleBinder forceBind(ModelReference<T> subject, ModelActionRole role, ModelAction mutator, ModelPath scope) {
        BindingPredicate mappedSubject = mapSubject(subject, role, scope);
        List<BindingPredicate> mappedInputs = mapInputs(mutator.getInputs(), scope);
        RuleBinder binder = new RuleBinder(mappedSubject, mappedInputs, mutator, unboundRules);
        ruleBindings.add(binder);
        return binder;
    }

    @Override
    public <T> T realize(String path, Class<T> type) {
        return realize(path, ModelType.of(type));
    }

    @Override
    public <T> T realize(String path, ModelType<T> type) {
        return realize(ModelPath.path(path), type);
    }

    @Override
    public <T> T realize(ModelPath path, ModelType<T> type) {
        return toType(type, require(path), "get(ModelPath, ModelType)");
    }

    public ModelNode atState(ModelPath path, ModelNode.State state) {
        return atStateOrMaybeLater(path, state, false);
    }

    @Override
    public ModelNode atStateOrLater(ModelPath path, ModelNode.State state) {
        return atStateOrMaybeLater(path, state, true);
    }

    @Override
    public <T> T atStateOrLater(ModelPath path, ModelType<T> type, ModelNode.State state) {
        return toType(type, atStateOrMaybeLater(path, state, true), "atStateOrLater(ModelPath, ModelType, ModelNode.State)");
    }

    private ModelNodeInternal atStateOrMaybeLater(ModelPath path, ModelNode.State state, boolean laterOk) {
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            throw new IllegalStateException("No model node at '" + path + "'");
        }
        transition(node, state, laterOk);
        return node;
    }

    @Override
    public <T> T find(String path, Class<T> type) {
        return find(path, ModelType.of(type));
    }

    @Override
    public <T> T find(String path, ModelType<T> type) {
        return find(ModelPath.path(path), type);
    }

    @Override
    public <T> T find(ModelPath path, ModelType<T> type) {
        return toType(type, get(path), "find(ModelPath, ModelType)");
    }

    private <T> T toType(ModelType<T> type, ModelNodeInternal node, String msg) {
        if (node == null) {
            return null;
        } else {
            return assertView(node, type, null, msg).getInstance();
        }
    }

    @Override
    public ModelNode realizeNode(ModelPath path) {
        return require(path);
    }

    private void registerListener(ModelListener listener) {
        modelGraph.addListener(listener);
    }

    public void remove(ModelPath path) {
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            return;
        }

        Iterable<? extends ModelNode> dependents = node.getDependents();
        if (Iterables.isEmpty(dependents)) {
            modelGraph.remove(node);
            ruleBindings.remove(node);
            unboundRules.removeAll(node.getInitializerRuleBinders());
        } else {
            throw new RuntimeException("Tried to remove model " + path + " but it is depended on by: " + Joiner.on(", ").join(dependents));
        }
    }

    @Override
    public ModelRegistry registerOrReplace(ModelRegistration newRegistration) {
        ModelPath path = newRegistration.getPath();
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            ModelNodeInternal parent = modelGraph.find(path.getParent());
            if (parent == null) {
                throw new IllegalStateException("Cannot create '" + path + "' as its parent node does not exist");
            }

            parent.addLink(newRegistration);
        } else {
            replace(newRegistration);
        }

        return this;
    }

    @Override
    public ModelRegistry replace(ModelRegistration newRegistration) {
        ModelNodeInternal node = modelGraph.find(newRegistration.getPath());
        if (node == null) {
            throw new IllegalStateException("can not replace node " + newRegistration.getPath() + " as it does not exist");
        }

        replace = true;
        try {
            boolean wasDiscovered = node.isAtLeast(Discovered);

            for (RuleBinder ruleBinder : node.getInitializerRuleBinders()) {
                ruleBindings.remove(node, ruleBinder);
            }
            node.getInitializerRuleBinders().clear();

            // Will internally verify that this is valid
            node.replaceRegistration(newRegistration);
            node.setState(Registered);
            addRuleBindings(node);
            if (wasDiscovered) {
                transition(node, Discovered, false);
            }
        } finally {
            replace = false;
        }
        return this;
    }

    public void bindAllReferences() throws UnboundModelRulesException {
        GoalGraph graph = new GoalGraph();
        for (ModelNodeInternal node : modelGraph.getFlattened().values()) {
            if (!node.isAtLeast(Discovered)) {
                transitionTo(graph, new Discover(node.getPath()));
            }
        }

        if (unboundRules.isEmpty()) {
            return;
        }
        boolean newInputsBound = true;
        while (!unboundRules.isEmpty() && newInputsBound) {
            newInputsBound = false;
            RuleBinder[] unboundBinders = unboundRules.toArray(new RuleBinder[unboundRules.size()]);
            for (RuleBinder binder : unboundBinders) {
                transitionTo(graph, new TryBindInputs(binder));
                newInputsBound = newInputsBound || binder.isBound();
            }
        }

        if (!unboundRules.isEmpty()) {
            SortedSet<RuleBinder> sortedBinders = new TreeSet<RuleBinder>(new Comparator<RuleBinder>() {
                @Override
                public int compare(RuleBinder o1, RuleBinder o2) {
                    return o1.getDescriptor().toString().compareTo(o2.getDescriptor().toString());
                }
            });
            sortedBinders.addAll(unboundRules);
            throw unbound(sortedBinders);
        }
    }

    private UnboundModelRulesException unbound(Iterable<? extends RuleBinder> binders) {
        ModelPathSuggestionProvider suggestionsProvider = new ModelPathSuggestionProvider(modelGraph.getFlattened().keySet());
        List<? extends UnboundRule> unboundRules = new UnboundRulesProcessor(binders, suggestionsProvider).process();
        return new UnboundModelRulesException(unboundRules);
    }

    private ModelNodeInternal require(ModelPath path) {
        ModelNodeInternal node = get(path);
        if (node == null) {
            throw new IllegalStateException("No model node at '" + path + "'");
        }
        return node;
    }

    @Override
    public ModelNode.State state(ModelPath path) {
        ModelNodeInternal modelNode = modelGraph.find(path);
        return modelNode == null ? null : modelNode.getState();
    }

    private ModelNodeInternal get(ModelPath path) {
        GoalGraph graph = new GoalGraph();
        transitionTo(graph, graph.nodeAtState(new NodeAtState(path, Registered)));
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            return null;
        }
        transitionTo(graph, graph.nodeAtState(new NodeAtState(path, GraphClosed)));
        return node;
    }

    /**
     * Attempts to achieve the given goal.
     */
    // TODO - reuse graph, discard state once not required
    private void transitionTo(GoalGraph goalGraph, ModelGoal targetGoal) {
        LinkedList<ModelGoal> queue = new LinkedList<ModelGoal>();
        queue.add(targetGoal);
        while (!queue.isEmpty()) {
            ModelGoal goal = queue.getFirst();

            if (goal.state == ModelGoal.State.Achieved) {
                // Already reached this goal
                queue.removeFirst();
                continue;
            }
            if (goal.state == ModelGoal.State.NotSeen) {
                if (goal.isAchieved()) {
                    // Goal has previously been achieved or is no longer required
                    goal.state = ModelGoal.State.Achieved;
                    queue.removeFirst();
                    continue;
                }
            }
            if (goal.state == ModelGoal.State.VisitingDependencies) {
                // All dependencies visited
                goal.apply();
                goal.state = ModelGoal.State.Achieved;
                queue.removeFirst();
                continue;
            }

            // Add dependencies for this goal
            List<ModelGoal> newDependencies = new ArrayList<ModelGoal>();
            goal.attachNode();
            boolean done = goal.calculateDependencies(goalGraph, newDependencies);
            goal.state = done || newDependencies.isEmpty() ? ModelGoal.State.VisitingDependencies : ModelGoal.State.DiscoveringDependencies;

            // Add dependencies to the start of the queue
            for (int i = newDependencies.size() - 1; i >= 0; i--) {
                ModelGoal dependency = newDependencies.get(i);
                if (dependency.state == ModelGoal.State.Achieved) {
                    continue;
                }
                if (dependency.state == ModelGoal.State.NotSeen) {
                    queue.addFirst(dependency);
                    continue;
                }
                throw ruleCycle(dependency, queue);
            }
        }
    }

    private ConfigurationCycleException ruleCycle(ModelGoal brokenGoal, LinkedList<ModelGoal> queue) {
        List<String> path = new ArrayList<String>();
        int pos = queue.indexOf(brokenGoal);
        ListIterator<ModelGoal> iterator = queue.listIterator(pos + 1);
        while (iterator.hasPrevious()) {
            ModelGoal goal = iterator.previous();
            goal.attachToCycle(path);
        }
        brokenGoal.attachToCycle(path);

        Formatter out = new Formatter();
        out.format("A cycle has been detected in model rule dependencies. References forming the cycle:");
        String last = null;
        StringBuilder indent = new StringBuilder("");
        for (int i = 0; i < path.size(); i++) {
            String node = path.get(i);
            // Remove duplicates
            if (node.equals(last)) {
                continue;
            }
            last = node;
            if (i == 0) {
                out.format("%n%s%s", indent, node);
            } else {
                out.format("%n%s\\- %s", indent, node);
                indent.append("   ");
            }
        }

        return new ConfigurationCycleException(out.toString());
    }

    private void transition(ModelNodeInternal node, ModelNode.State desired, boolean laterOk) {
        ModelPath path = node.getPath();
        ModelNode.State state = node.getState();

        LOGGER.debug("Transitioning model element '{}' from state {} to {}", path, state.name(), desired.name());

        if (desired.ordinal() < state.ordinal()) {
            if (laterOk) {
                return;
            } else {
                throw new IllegalStateException("Cannot lifecycle model node '" + path + "' to state " + desired.name() + " as it is already at " + state.name());
            }
        }

        if (state == desired) {
            return;
        }

        GoalGraph goalGraph = new GoalGraph();
        transitionTo(goalGraph, goalGraph.nodeAtState(new NodeAtState(node.getPath(), desired)));
    }

    private <T> ModelView<? extends T> assertView(ModelNodeInternal node, ModelType<T> targetType, @Nullable ModelRuleDescriptor descriptor, String msg, Object... msgArgs) {
        ModelView<? extends T> view = node.asImmutable(targetType, descriptor);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model node '" + node.getPath().toString() + "' is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return view;
        }
    }

    private void fireAction(RuleBinder boundMutator) {
        final List<ModelView<?>> inputs = toViews(boundMutator.getInputBindings(), boundMutator.getAction().getDescriptor());
        ModelBinding subjectBinding = boundMutator.getSubjectBinding();
        final ModelNodeInternal node = subjectBinding.getNode();
        final ModelAction mutator = boundMutator.getAction();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        LOGGER.debug("Mutating {} using {}", node.getPath(), descriptor);

        try {
            RuleContext.run(descriptor, new Runnable() {
                @Override
                public void run() {
                    mutator.execute(node, inputs);
                }
            });
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(descriptor, e);
        }
    }

    private List<ModelView<?>> toViews(List<ModelBinding> bindings, ModelRuleDescriptor descriptor) {
        // hot path; create as little as possible…
        @SuppressWarnings("unchecked") ModelView<?>[] array = new ModelView<?>[bindings.size()];
        int i = 0;
        for (ModelBinding binding : bindings) {
            ModelNodeInternal element = binding.getNode();
            ModelView<?> view = assertView(element, binding.getPredicate().getType(), descriptor, "toViews");
            array[i++] = view;
        }
        @SuppressWarnings("unchecked") List<ModelView<?>> views = Arrays.asList(array);
        return views;
    }

    @Override
    public MutableModelNode getRoot() {
        return modelGraph.getRoot();
    }

    @Override
    public MutableModelNode node(ModelPath path) {
        return modelGraph.find(path);
    }

    @Override
    public void prepareForReuse() {
        reset = true;
        List<ModelNodeInternal> ephemerals = Lists.newLinkedList();
        collectEphemeralChildren(modelGraph.getRoot(), ephemerals);
        if (ephemerals.isEmpty()) {
            LOGGER.info("No ephemeral model nodes found to reset");
        } else {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Resetting ephemeral model nodes: " + Joiner.on(", ").join(ephemerals));
            }

            for (ModelNodeInternal ephemeral : ephemerals) {
                ephemeral.reset();
            }
        }
    }

    private void collectEphemeralChildren(ModelNodeInternal node, Collection<ModelNodeInternal> ephemerals) {
        for (ModelNodeInternal child : node.getLinks()) {
            if (child.isEphemeral()) {
                ephemerals.add(child);
            } else {
                collectEphemeralChildren(child, ephemerals);
            }
        }
    }

    private BindingPredicate mapSubject(ModelReference<?> subjectReference, ModelActionRole role, ModelPath scope) {
        if (!role.isSubjectViewAvailable() && !subjectReference.isUntyped()) {
            throw new IllegalStateException(String.format("Cannot bind subject '%s' to role '%s' because it is targeting a type and subject types are not yet available in that role", subjectReference, role));
        }
        ModelReference<?> mappedReference;
        if (subjectReference.getPath() == null) {
            mappedReference = subjectReference.inScope(scope);
        } else {
            mappedReference = subjectReference.withPath(scope.descendant(subjectReference.getPath()));
        }
        return new BindingPredicate(mappedReference.atState(role.getTargetState()));
    }

    private List<BindingPredicate> mapInputs(List<? extends ModelReference<?>> inputs, ModelPath scope) {
        if (inputs.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<BindingPredicate> result = new ArrayList<BindingPredicate>(inputs.size());
        for (ModelReference<?> input : inputs) {
            if (input.getPath() != null) {
                result.add(new BindingPredicate(input.withPath(scope.descendant(input.getPath()))));
            } else {
                result.add(new BindingPredicate(input.inScope(ModelPath.ROOT)));
            }
        }
        return result;
    }

    private class ModelElementNode extends ModelNodeInternal {
        private final Map<String, ModelNodeInternal> links = Maps.newTreeMap();
        private final MutableModelNode parent;
        private Object privateData;
        private ModelType<?> privateDataType;

        public ModelElementNode(ModelRegistration registration, MutableModelNode parent) {
            super(registration);
            this.parent = parent;
        }

        @Override
        public MutableModelNode getParent() {
            return parent;
        }

        @Override
        public boolean canBeViewedAs(ModelType<?> type) {
            return getPromise().canBeViewedAsImmutable(type) || getPromise().canBeViewedAsMutable(type);
        }

        @Override
        public <T> ModelView<? extends T> asImmutable(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
            ModelView<? extends T> modelView = getAdapter().asImmutable(type, this, ruleDescriptor);
            if (modelView == null) {
                throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a read-only view of type " + type);
            }
            return modelView;
        }

        @Override
        public <T> ModelView<? extends T> asMutable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
            ModelView<? extends T> modelView = getAdapter().asMutable(type, this, ruleDescriptor, inputs);
            if (modelView == null) {
                throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a mutable view of type " + type);
            }
            return modelView;
        }

        @Override
        public <T> T getPrivateData(Class<T> type) {
            return getPrivateData(ModelType.of(type));
        }

        public <T> T getPrivateData(ModelType<T> type) {
            if (privateData == null) {
                return null;
            }

            if (!type.isAssignableFrom(privateDataType)) {
                throw new ClassCastException("Cannot get private data '" + privateData + "' of type '" + privateDataType + "' as type '" + type);
            }
            return Cast.uncheckedCast(privateData);
        }

        @Override
        public Object getPrivateData() {
            return privateData;
        }

        @Override
        public <T> void setPrivateData(Class<? super T> type, T object) {
            setPrivateData(ModelType.of(type), object);
        }

        public <T> void setPrivateData(ModelType<? super T> type, T object) {
            if (!isMutable()) {
                throw new IllegalStateException(String.format("Cannot set value for model element '%s' as this element is not mutable.", getPath()));
            }
            this.privateDataType = type;
            this.privateData = object;
        }

        @Override
        protected void resetPrivateData() {
            this.privateDataType = null;
            this.privateData = null;
        }

        public boolean hasLink(String name) {
            return links.containsKey(name);
        }

        @Nullable
        public ModelNodeInternal getLink(String name) {
            return links.get(name);
        }

        public Iterable<? extends ModelNodeInternal> getLinks() {
            return links.values();
        }

        @Override
        public int getLinkCount(ModelType<?> type) {
            int count = 0;
            for (ModelNodeInternal linked : links.values()) {
                linked.ensureAtLeast(Discovered);
                if (linked.getPromise().canBeViewedAsMutable(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Set<String> getLinkNames(ModelType<?> type) {
            Set<String> names = Sets.newLinkedHashSet();
            for (Map.Entry<String, ModelNodeInternal> entry : links.entrySet()) {
                ModelNodeInternal link = entry.getValue();
                link.ensureAtLeast(Discovered);
                if (link.getPromise().canBeViewedAsMutable(type)) {
                    names.add(entry.getKey());
                }
            }
            return names;
        }

        @Override
        public Iterable<? extends MutableModelNode> getLinks(final ModelType<?> type) {
            return Iterables.filter(links.values(), new Predicate<ModelNodeInternal>() {
                @Override
                public boolean apply(ModelNodeInternal link) {
                    link.ensureAtLeast(Discovered);
                    return link.getPromise().canBeViewedAsMutable(type);
                }
            });
        }

        @Override
        public int getLinkCount() {
            return links.size();
        }

        @Override
        public boolean hasLink(String name, ModelType<?> type) {
            ModelNodeInternal linked = getLink(name);
            if (linked == null) {
                return false;
            }
            linked.ensureAtLeast(Discovered);
            return linked.getPromise().canBeViewedAsMutable(type);
        }

        @Override
        public void applyToSelf(ModelActionRole role, ModelAction action) {
            checkNodePath(this, action);
            bind(action.getSubject(), role, action, ModelPath.ROOT);
        }

        @Override
        public void applyToLink(ModelActionRole type, ModelAction action) {
            if (!getPath().isDirectChild(action.getSubject().getPath())) {
                throw new IllegalArgumentException(String.format("Linked element action reference has a path (%s) which is not a child of this node (%s).", action.getSubject().getPath(), getPath()));
            }
            bind(action.getSubject(), type, action, ModelPath.ROOT);
        }

        @Override
        public void applyToLink(String name, Class<? extends RuleSource> rules) {
            apply(rules, getPath().child(name));
        }

        @Override
        public void applyToSelf(Class<? extends RuleSource> rules) {
            apply(rules, getPath());
        }

        @Override
        public void applyToLinks(final ModelType<?> type, final Class<? extends RuleSource> rules) {
            registerListener(new ModelListener() {
                @Nullable
                @Override
                public ModelPath getParent() {
                    return getPath();
                }

                @Nullable
                @Override
                public ModelType<?> getType() {
                    return type;
                }

                @Override
                public boolean onDiscovered(ModelNodeInternal node) {
                    node.applyToSelf(rules);
                    return false;
                }
            });
        }

        @Override
        public void applyToAllLinksTransitive(final ModelType<?> type, final Class<? extends RuleSource> rules) {
            registerListener(new ModelListener() {
                @Override
                public ModelPath getAncestor() {
                    return ModelElementNode.this.getPath();
                }

                @Nullable
                @Override
                public ModelType<?> getType() {
                    return type;
                }

                @Override
                public boolean onDiscovered(ModelNodeInternal node) {
                    node.applyToSelf(rules);
                    return false;
                }
            });
        }

        private void apply(Class<? extends RuleSource> rules, ModelPath scope) {
            Iterable<ExtractedModelRule> extractedRules = ruleExtractor.extract(rules);
            for (ExtractedModelRule extractedRule : extractedRules) {
                if (!extractedRule.getRuleDependencies().isEmpty()) {
                    throw new IllegalStateException("Rule source " + rules + " cannot have plugin dependencies (introduced by rule " + extractedRule + ")");
                }
                extractedRule.apply(DefaultModelRegistry.this, scope);
            }
        }

        @Override
        public void applyToAllLinks(final ModelActionRole type, final ModelAction action) {
            if (action.getSubject().getPath() != null) {
                throw new IllegalArgumentException("Linked element action reference must have null path.");
            }

            registerListener(new ModelListener() {
                @Override
                public ModelPath getParent() {
                    return ModelElementNode.this.getPath();
                }

                @Override
                public ModelType<?> getType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onDiscovered(ModelNodeInternal node) {
                    bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    return false;
                }
            });
        }

        @Override
        public void applyToAllLinksTransitive(final ModelActionRole type, final ModelAction action) {
            if (action.getSubject().getPath() != null) {
                throw new IllegalArgumentException("Linked element action reference must have null path.");
            }

            registerListener(new ModelListener() {
                @Override
                public ModelPath getAncestor() {
                    return ModelElementNode.this.getPath();
                }

                @Override
                public ModelType<?> getType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onDiscovered(ModelNodeInternal node) {
                    bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    return false;
                }
            });
        }

        @Override
        public void addReference(ModelRegistration registration) {
            addNode(new ModelReferenceNode(registration, this), registration);
        }

        @Override
        public void addLink(ModelRegistration registration) {
            addNode(new ModelElementNode(registration, this), registration);
        }

        private void addNode(ModelNodeInternal child, ModelRegistration registration) {
            ModelPath childPath = child.getPath();
            if (!getPath().isDirectChild(childPath)) {
                throw new IllegalArgumentException(String.format("Element registration has a path (%s) which is not a child of this node (%s).", childPath, getPath()));
            }

            if (reset) {
                // Reuse child node
                registerNode(child);
                return;
            }

            ModelNodeInternal currentChild = links.get(childPath.getName());
            if (currentChild != null) {
                if (!currentChild.isAtLeast(Created)) {
                    throw new DuplicateModelException(
                        String.format(
                            "Cannot create '%s' using creation rule '%s' as the rule '%s' is already registered to create this model element.",
                            childPath,
                            describe(registration.getDescriptor()),
                            describe(currentChild.getDescriptor())
                        )
                    );
                }
                throw new DuplicateModelException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as the rule '%s' has already been used to create this model element.",
                        childPath,
                        describe(registration.getDescriptor()),
                        describe(currentChild.getDescriptor())
                    )
                );
            }
            if (!isMutable()) {
                throw new IllegalStateException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as model element '%s' is no longer mutable.",
                        childPath,
                        describe(registration.getDescriptor()),
                        getPath()
                    )
                );
            }
            links.put(child.getPath().getName(), child);
            registerNode(child);
        }

        @Override
        public void removeLink(String name) {
            if (links.remove(name) != null) {
                remove(getPath().child(name));
            }
        }

        @Override
        public void setTarget(ModelNode target) {
            throw new UnsupportedOperationException(String.format("This node (%s) is not a reference to another node.", getPath()));
        }

        @Override
        public void ensureUsable() {
            ensureAtLeast(Initialized);
        }

        @Override
        public void ensureAtLeast(State state) {
            transition(this, state, true);
        }
    }

    private class GoalGraph {
        private final Map<NodeAtState, ModelGoal> nodeStates = new HashMap<NodeAtState, ModelGoal>();

        public ModelGoal nodeAtState(NodeAtState goal) {
            ModelGoal node = nodeStates.get(goal);
            if (node == null) {
                switch (goal.state) {
                    case Registered:
                        node = new MakeKnown(goal.path);
                        break;
                    case Discovered:
                        node = new Discover(goal.path);
                        break;
                    case GraphClosed:
                        node = new CloseGraph(goal);
                        break;
                    default:
                        node = new ApplyActions(goal);
                }
                nodeStates.put(goal, node);
            }
            return node;
        }
    }

    /**
     * Some abstract goal that must be achieved in the model graph.
     */
    private abstract static class ModelGoal {
        enum State {
            NotSeen,
            DiscoveringDependencies,
            VisitingDependencies,
            Achieved,
        }

        public State state = State.NotSeen;

        /**
         * Determines whether the goal has already been achieved. Invoked prior to traversing any dependencies of this goal, and if true is returned the dependencies of this goal are not traversed and
         * the action not applied.
         */
        public boolean isAchieved() {
            return false;
        }

        /**
         * Invoked prior to calculating dependencies.
         */
        public void attachNode() {
        }

        /**
         * Calculates any dependencies for this goal. May be invoked multiple times, should only add newly dependencies discovered dependencies on each invocation.
         *
         * <p>The dependencies returned by this method are all traversed before this method is called another time.</p>
         *
         * @return true if this goal will be ready to apply once the returned dependencies have been achieved. False if additional dependencies for this goal may be discovered during the execution of
         * the known dependencies.
         */
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            return true;
        }

        /**
         * Applies the action of this goal.
         */
        void apply() {
        }

        void attachToCycle(List<String> displayValue) {
        }

        @Override
        public abstract String toString();
    }

    /**
     * Some abstract goal to be achieve for a particular node in the model graph.
     */
    private abstract class ModelNodeGoal extends ModelGoal {
        public final ModelPath target;
        public ModelNodeInternal node;

        protected ModelNodeGoal(ModelPath target) {
            this.target = target;
        }

        public ModelPath getPath() {
            return target;
        }

        @Override
        public final boolean isAchieved() {
            node = modelGraph.find(target);
            return node != null && doIsAchieved();
        }

        /**
         * Invoked only if node is known prior to traversing dependencies of this goal
         */
        protected boolean doIsAchieved() {
            return false;
        }

        @Override
        public void attachNode() {
            if (node != null) {
                return;
            }
            node = modelGraph.find(getPath());
        }
    }

    private class MakeKnown extends ModelNodeGoal {
        public MakeKnown(ModelPath path) {
            super(path);
        }

        @Override
        public String toString() {
            return "make known " + getPath() + ", state: " + state;
        }

        @Override
        public boolean doIsAchieved() {
            // Only called when node exists, therefore node is known
            return true;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            // Not already known, attempt to self-close the parent
            ModelPath parent = getPath().getParent();
            if (parent != null) {
                // TODO - should be >= self closed
                dependencies.add(graph.nodeAtState(new NodeAtState(parent, SelfClosed)));
            }
            return true;
        }
    }

    private abstract class TransitionNodeToState extends ModelNodeGoal {
        final NodeAtState target;
        private boolean seenPredecessor;

        public TransitionNodeToState(NodeAtState target) {
            super(target.path);
            this.target = target;
        }

        @Override
        public String toString() {
            return "transition " + getPath() + ", target: " + target.state + ", state: " + state;
        }

        public ModelNode.State getTargetState() {
            return target.state;
        }

        @Override
        public boolean doIsAchieved() {
            return node.getState().compareTo(getTargetState()) >= 0;
        }

        @Override
        public final boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (!seenPredecessor) {
                // Node must be at the predecessor state before calculating dependencies
                NodeAtState predecessor = new NodeAtState(getPath(), getTargetState().previous());
                dependencies.add(graph.nodeAtState(predecessor));
                // Transition any other nodes that depend on the predecessor state
                dependencies.add(new TransitionDependents(predecessor));
                seenPredecessor = true;
                return false;
            }
            if (node == null) {
                throw new IllegalStateException(String.format("Cannot transition model element '%s' to state %s as it does not exist.", getPath(), getTargetState().name()));
            }
            return doCalculateDependencies(graph, dependencies);
        }

        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            return true;
        }

        @Override
        public final void apply() {
            if (!node.getState().equals(getTargetState().previous())) {
                throw new IllegalStateException(String.format("Cannot transition model element '%s' to state %s as it is already at state %s.", node.getPath(), getTargetState(), node.getState()));
            }
            LOGGER.debug("Transitioning model element '{}' to state {}.", node.getPath(), getTargetState().name());
            node.setState(getTargetState());
        }

        @Override
        void attachToCycle(List<String> displayValue) {
            displayValue.add(getPath().toString());
        }
    }

    private class Discover extends ModelNodeGoal {
        public Discover(ModelPath path) {
            super(path);
        }

        @Override
        public boolean doIsAchieved() {
            return node.isAtLeast(Discovered);
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            dependencies.add(new ApplyActions(new NodeAtState(getPath(), Discovered)));
            dependencies.add(new NotifyDiscovered(getPath()));
            return true;
        }

        @Override
        public String toString() {
            return "discover " + getPath() + ", state: " + state;
        }
    }

    private class TransitionDependents extends ModelGoal {
        private final NodeAtState input;

        public TransitionDependents(NodeAtState input) {
            this.input = input;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            for (RuleBinder rule : ruleBindings.getRulesWithInput(input)) {
                ModelBinding subjectBinding = rule.getSubjectBinding();
                if (!subjectBinding.isBound()) {
                    // TODO - implement these cases
                    continue;
                }
                ModelPath targetPath = subjectBinding.getNode().getPath();
                if (targetPath.equals(input.path)) {
                    // Ignore future states of the input node
                    continue;
                }
                ModelNode.State targetState = subjectBinding.getPredicate().getState();
                dependencies.add(graph.nodeAtState(new NodeAtState(targetPath, targetState)));
            }
            return true;
        }

        @Override
        public String toString() {
            return "transition dependents " + input.path + ", target: " + input.state + ", state: " + state;
        }
    }

    private class TransitionChildrenOrReference extends ModelNodeGoal {

        private final ModelNode.State targetState;

        protected TransitionChildrenOrReference(ModelPath target, ModelNode.State targetState) {
            super(target);
            this.targetState = targetState;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (node instanceof ModelReferenceNode) {
                ModelReferenceNode referenceNode = (ModelReferenceNode) node;
                ModelNodeInternal target = referenceNode.getTarget();
                if (target == null || target.getPath().isDescendant(node.getPath())) {
                    // No target, or target is an ancestor of this node, so is already being handled
                    return true;
                }
                if (!target.isAtLeast(targetState)) {
                    dependencies.add(graph.nodeAtState(new NodeAtState(target.getPath(), targetState)));
                }
            } else {
                for (ModelNodeInternal child : node.getLinks()) {
                    if (!child.isAtLeast(targetState)) {
                        dependencies.add(graph.nodeAtState(new NodeAtState(child.getPath(), targetState)));
                    }
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "transition children of " + getPath() + " to " + targetState + ", state: " + state;
        }
    }

    private class ApplyActions extends TransitionNodeToState {
        private final Set<RuleBinder> seenRules = new HashSet<RuleBinder>();

        public ApplyActions(NodeAtState target) {
            super(target);
        }

        @Override
        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            boolean noActionsAdded = true;
            // Must run each action
            for (RuleBinder binder : ruleBindings.getRulesWithSubject(target)) {
                if (seenRules.add(binder)) {
                    noActionsAdded = false;
                    dependencies.add(new RunModelAction(getPath(), binder));
                }
            }
            return noActionsAdded;
        }
    }

    private class CloseGraph extends TransitionNodeToState {
        public CloseGraph(NodeAtState target) {
            super(target);
        }

        @Override
        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            dependencies.add(new TransitionChildrenOrReference(getPath(), GraphClosed));
            return true;
        }
    }

    /**
     * Attempts to make known the given path. When the path references a link, also makes the target of the link known.
     *
     * Does not fail if not possible to do.
     */
    private class TryResolvePath extends ModelNodeGoal {
        private boolean attemptedParent;

        public TryResolvePath(ModelPath path) {
            super(path);
        }

        @Override
        protected boolean doIsAchieved() {
            // Only called when node exists
            return true;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            // Not already known, attempt to resolve the parent
            if (!attemptedParent) {
                dependencies.add(new TryResolvePath(getPath().getParent()));
                attemptedParent = true;
                return false;
            }

            ModelNodeInternal parent = modelGraph.find(getPath().getParent());
            if (parent == null) {
                // No parent, we're done
                return true;
            }
            if (parent instanceof ModelReferenceNode) {
                // Parent is a reference, need to resolve the target
                ModelReferenceNode parentReference = (ModelReferenceNode) parent;
                if (parentReference.getTarget() != null) {
                    dependencies.add(new TryResolveReference(parentReference, getPath()));
                }
            } else {
                // Self close parent in order to discover its children, or its target in the case of a reference
                dependencies.add(graph.nodeAtState(new NodeAtState(getPath().getParent(), SelfClosed)));
            }

            return true;
        }

        @Override
        public String toString() {
            return "try resolve path " + getPath() + ", state: " + state;
        }
    }

    private class TryResolveAndDiscoverPath extends TryResolvePath {

        private boolean attemptedPath;

        public TryResolveAndDiscoverPath(ModelPath path) {
            super(path);
        }

        @Override
        protected boolean doIsAchieved() {
            return node.isAtLeast(Discovered);
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (modelGraph.find(getPath()) == null) {
                if (!attemptedPath) {
                    attemptedPath = super.calculateDependencies(graph, dependencies);
                    return false;
                } else {
                    // Didn't find node at path
                    return true;
                }
            }
            dependencies.add(graph.nodeAtState(new NodeAtState(getPath(), Discovered)));
            return true;
        }
    }

    private class TryResolveReference extends ModelGoal {
        private final ModelReferenceNode parent;
        private final ModelPath path;

        public TryResolveReference(ModelReferenceNode parent, ModelPath path) {
            this.parent = parent;
            this.path = path;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            dependencies.add(new TryResolveAndDiscoverPath(parent.getTarget().getPath().child(path.getName())));
            return true;
        }

        @Override
        void apply() {
            // Rough implementation to get something to work
            ModelNodeInternal parentTarget = parent.getTarget();
            ModelNodeInternal childTarget = parentTarget.getLink(path.getName());
            if (childTarget == null) {
                throw new NullPointerException("child is null");
            }
            // TODO:LPTR Remove projection for reference node
            // This shouldn't be needed, but if there's no actual value referenced, model report can only
            // show the type of the node if we do this for now. It should use the schema instead to find
            // the type of the property node instead.
            ModelRegistration registration = ModelRegistrations.of(path)
                .descriptor(parent.getDescriptor())
                .withProjection(childTarget.getProjection())
                .build();
            ModelReferenceNode childNode = new ModelReferenceNode(registration, parent);
            childNode.setTarget(childTarget);
            registerNode(childNode);
            ruleBindings.nodeDiscovered(childNode);
        }

        @Override
        public String toString() {
            return "try resolve reference " + path + ", state: " + state;
        }
    }

    /**
     * Attempts to define the contents of the requested scope. Does not fail if not possible.
     */
    private class TryDefineScopeForType extends ModelGoal {
        private final ModelPath scope;
        private final ModelType<?> typeToBind;
        private boolean attemptedPath;
        private boolean attemptedCloseScope;

        public TryDefineScopeForType(ModelPath scope, ModelType<?> typeToBind) {
            this.scope = scope;
            this.typeToBind = typeToBind;
        }

        @Override
        public boolean isAchieved() {
            ModelNodeInternal node = modelGraph.find(scope);
            if (node == null) {
                return false;
            }
            for (ModelNodeInternal child : node.getLinks()) {
                if (child.isAtLeast(Discovered) && (child.getPromise().canBeViewedAsImmutable(typeToBind) || child.getPromise().canBeViewedAsMutable(typeToBind))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (!attemptedPath) {
                dependencies.add(new TryResolvePath(scope));
                attemptedPath = true;
                return false;
            }
            if (modelGraph.find(scope) != null) {
                if (!attemptedCloseScope) {
                    dependencies.add(graph.nodeAtState(new NodeAtState(scope, SelfClosed)));
                    attemptedCloseScope = true;
                    return false;
                } else {
                    dependencies.add(new TransitionChildrenOrReference(scope, Discovered));
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "try define scope " + scope + " to bind type " + typeToBind + ", state: " + state;
        }
    }

    /**
     * Attempts to bind the inputs of a rule. Does not fail if not possible to bind all inputs.
     */
    private class TryBindInputs extends ModelGoal {
        private final RuleBinder binder;

        public TryBindInputs(RuleBinder binder) {
            this.binder = binder;
        }

        @Override
        public String toString() {
            return "bind inputs for " + binder.getDescriptor() + ", state: " + state;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            // Shouldn't really be here. Currently this goal is used by {@link #bindAllReferences} which also expects the subject to be bound
            maybeBind(binder.getSubjectBinding(), dependencies);
            for (ModelBinding binding : binder.getInputBindings()) {
                maybeBind(binding, dependencies);
            }
            return true;
        }

        private void maybeBind(ModelBinding binding, Collection<ModelGoal> dependencies) {
            if (!binding.isBound()) {
                if (binding.getPredicate().getPath() != null) {
                    dependencies.add(new TryResolveAndDiscoverPath(binding.getPredicate().getPath()));
                } else {
                    dependencies.add(new TryDefineScopeForType(binding.getPredicate().getScope(), binding.getPredicate().getType()));
                }
            }
        }
    }

    private abstract class RunAction extends ModelNodeGoal {
        private final RuleBinder binder;
        private boolean bindInputs;

        public RunAction(ModelPath path, RuleBinder binder) {
            super(path);
            this.binder = binder;
        }

        @Override
        public String toString() {
            return "run action for " + getPath() + ", rule: " + binder.getDescriptor() + ", state: " + state;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (!bindInputs) {
                // Must prepare to bind inputs first
                dependencies.add(new TryBindInputs(binder));
                bindInputs = true;
                return false;
            }
            // Must close each input first
            if (!binder.isBound()) {
                throw unbound(Collections.singleton(binder));
            }
            for (ModelBinding binding : binder.getInputBindings()) {
                dependencies.add(graph.nodeAtState(new NodeAtState(binding.getNode().getPath(), binding.getPredicate().getState())));
            }
            return true;
        }

        @Override
        void attachToCycle(List<String> displayValue) {
            displayValue.add(binder.getDescriptor().toString());
        }
    }

    private class RunModelAction extends RunAction {
        private final RuleBinder binder;

        public RunModelAction(ModelPath path, RuleBinder binder) {
            super(path, binder);
            this.binder = binder;
        }

        @Override
        void apply() {
            LOGGER.debug("Running model element '{}' rule action {}", getPath(), binder.getDescriptor());
            fireAction(binder);
            node.notifyFired(binder);
        }
    }

    private class NotifyDiscovered extends ModelNodeGoal {

        protected NotifyDiscovered(ModelPath target) {
            super(target);
        }

        @Override
        void apply() {
            if (replace) {
                return;
            }
            ruleBindings.nodeDiscovered(node);
            modelGraph.nodeDiscovered(node);
        }

        @Override
        public String toString() {
            return "notify discovered for " + getPath() + ", state: " + state;
        }
    }
}
