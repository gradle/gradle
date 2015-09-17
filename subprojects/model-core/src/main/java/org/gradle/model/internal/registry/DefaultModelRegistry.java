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
import org.gradle.internal.BiActions;
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

    boolean reset;

    public DefaultModelRegistry(ModelRuleExtractor ruleExtractor) {
        this.ruleExtractor = ruleExtractor;
        ModelCreator rootCreator = ModelCreators.of(ModelPath.ROOT, BiActions.doNothing()).descriptor("<root>").withProjection(EmptyModelProjection.INSTANCE).build();
        modelGraph = new ModelGraph(new ModelElementNode(toCreatorBinder(rootCreator, ModelPath.ROOT), null));
        modelGraph.getRoot().setState(Created);
        ruleBindings = new RuleBindings(modelGraph);
    }

    private static String describe(ModelRuleDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    public DefaultModelRegistry create(ModelCreator creator) {
        ModelPath path = creator.getPath();
        if (!ModelPath.ROOT.isDirectChild(path)) {
            throw new InvalidModelRuleDeclarationException(creator.getDescriptor(), "Cannot create element at '" + path + "', only top level is allowed (e.g. '" + path.getRootParent() + "')");
        }

        ModelNodeInternal root = modelGraph.getRoot();
        root.addLink(creator);
        return this;
    }

    private CreatorRuleBinder toCreatorBinder(ModelCreator creator, ModelPath scope) {
        BindingPredicate subject = new BindingPredicate(ModelReference.of(creator.getPath(), ModelType.untyped(), ModelNode.State.Created));
        List<BindingPredicate> inputs = mapInputs(creator.getInputs(), scope);
        return new CreatorRuleBinder(creator, subject, inputs, unboundRules);
    }

    private ModelNodeInternal registerNode(ModelNodeInternal node) {
        if (reset) {
            unboundRules.remove(node.getCreatorBinder());
            return node;
        }

        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);

        ruleBindings.add(node);
        modelGraph.add(node);
        ruleBindings.add(node.getCreatorBinder());
        return node;
    }

    @Override
    public <T> DefaultModelRegistry configure(ModelActionRole role, ModelAction<T> action) {
        bind(action.getSubject(), role, action, ModelPath.ROOT);
        return this;
    }

    @Override
    public <T> ModelRegistry configure(ModelActionRole role, ModelAction<T> action, ModelPath scope) {
        bind(action.getSubject(), role, action, scope);
        return this;
    }

    @Override
    public ModelRegistry apply(Class<? extends RuleSource> rules) {
        modelGraph.getRoot().applyToSelf(rules);
        return this;
    }

    private <T> void bind(ModelReference<T> subject, ModelActionRole role, ModelAction<T> mutator, ModelPath scope) {
        if (reset) {
            return;
        }
        BindingPredicate mappedSubject = mapSubject(subject, role, scope);
        List<BindingPredicate> mappedInputs = mapInputs(mutator.getInputs(), scope);
        MutatorRuleBinder<T> binder = new MutatorRuleBinder<T>(mappedSubject, mappedInputs, mutator, unboundRules);
        ruleBindings.add(binder);
    }

    public <T> T realize(ModelPath path, ModelType<T> type) {
        return toType(type, require(path), "get(ModelPath, ModelType)");
    }

    @Override
    public ModelNode atState(ModelPath path, ModelNode.State state) {
        return atStateOrMaybeLater(path, state, false);
    }

    @Override
    public ModelNode atStateOrLater(ModelPath path, ModelNode.State state) {
        return atStateOrMaybeLater(path, state, true);
    }

    private ModelNode atStateOrMaybeLater(ModelPath path, ModelNode.State state, boolean laterOk) {
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            return null;
        }
        transition(node, state, laterOk);
        return node;
    }

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

    private void registerListener(ModelCreationListener listener) {
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
            unboundRules.remove(node.getCreatorBinder());
        } else {
            throw new RuntimeException("Tried to remove model " + path + " but it is depended on by: " + Joiner.on(", ").join(dependents));
        }
    }

    @Override
    public ModelRegistry createOrReplace(ModelCreator newCreator) {
        ModelPath path = newCreator.getPath();
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            ModelNodeInternal parent = modelGraph.find(path.getParent());
            if (parent == null) {
                throw new IllegalStateException("Cannot create '" + path + "' as its parent node does not exist");
            }

            parent.addLink(newCreator);
        } else {
            replace(newCreator);
        }

        return this;
    }

    @Override
    public ModelRegistry replace(ModelCreator newCreator) {
        ModelNodeInternal node = modelGraph.find(newCreator.getPath());
        if (node == null) {
            throw new IllegalStateException("can not replace node " + newCreator.getPath() + " as it does not exist");
        }

        // Will internally verify that this is valid
        node.replaceCreatorRuleBinder(toCreatorBinder(newCreator, ModelPath.ROOT));
        ruleBindings.add(node.getCreatorBinder());
        return this;
    }

    public void bindAllReferences() throws UnboundModelRulesException {
        if (unboundRules.isEmpty()) {
            return;
        }

        GoalGraph graph = new GoalGraph();
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
        transitionTo(graph, graph.nodeAtState(new NodeAtState(path, Known)));
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
        ModelView<? extends T> view = node.asReadOnly(targetType, descriptor);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model node '" + node.getPath().toString() + "' is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return view;
        }
    }

    private <T> ModelView<? extends T> assertView(ModelNodeInternal node, ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelView<?>> inputs) {
        ModelView<? extends T> view = node.asWritable(reference.getType(), sourceDescriptor, inputs);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Cannot project model element " + node.getPath() + " to writable type '" + reference.getType() + "' for rule " + sourceDescriptor);
        } else {
            return view;
        }
    }

    private ModelNodeInternal doCreate(final ModelNodeInternal node, CreatorRuleBinder boundCreator) {
        final ModelCreator creator = boundCreator.getCreator();
        final List<ModelView<?>> views = toViews(boundCreator.getInputBindings(), boundCreator.getCreator());

        LOGGER.debug("Creating {} using {}", node.getPath(), creator.getDescriptor());

        try {
            RuleContext.run(creator.getDescriptor(), new Runnable() {
                @Override
                public void run() {
                    creator.create(node, views);
                }
            });
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getDescriptor(), e);
        }

        return node;
    }

    private <T> void fireMutation(MutatorRuleBinder<T> boundMutator) {
        final List<ModelView<?>> inputs = toViews(boundMutator.getInputBindings(), boundMutator.getAction());

        final ModelNodeInternal node = boundMutator.getSubjectBinding().getNode();
        final ModelAction<T> mutator = boundMutator.getAction();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        LOGGER.debug("Mutating {} using {}", node.getPath(), mutator.getDescriptor());

        ModelReference<T> reference = Cast.uncheckedCast(boundMutator.getSubjectReference().getReference());
        final ModelView<? extends T> view = assertView(node, reference, descriptor, inputs);
        try {
            RuleContext.run(descriptor, new Runnable() {
                @Override
                public void run() {
                    mutator.execute(node, view.getInstance(), inputs);
                }
            });
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(descriptor, e);
        } finally {
            view.close();
        }
    }

    private List<ModelView<?>> toViews(List<ModelBinding> bindings, ModelRule modelRule) {
        // hot path; create as little as possible…
        @SuppressWarnings("unchecked") ModelView<?>[] array = new ModelView<?>[bindings.size()];
        int i = 0;
        for (ModelBinding binding : bindings) {
            ModelNodeInternal element = binding.getNode();
            ModelView<?> view = assertView(element, binding.getPredicate().getType(), modelRule.getDescriptor(), "toViews");
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
        ModelReference<?> mappedReference;
        if (subjectReference.getPath() == null) {
            mappedReference = subjectReference.inScope(scope);
        } else {
            mappedReference = subjectReference.withPath(scope.descendant(subjectReference.getPath()));
        }
        if (role.getTargetState() != null) {
            return new BindingPredicate(mappedReference.atState(role.getTargetState()));
        } else {
            return new AnyStateBindingPredicate(mappedReference);
        }
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

        public ModelElementNode(CreatorRuleBinder creatorRuleBinder, MutableModelNode parent) {
            super(creatorRuleBinder);
            this.parent = parent;
        }

        @Override
        public MutableModelNode getParent() {
            return parent;
        }

        @Override
        public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
            ModelView<? extends T> modelView = getAdapter().asReadOnly(type, this, ruleDescriptor);
            if (modelView == null) {
                throw new IllegalStateException("Model node " + getPath() + " cannot be expressed as a read-only view of type " + type);
            }
            return modelView;
        }

        @Override
        public <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
            ModelView<? extends T> modelView = getAdapter().asWritable(type, this, ruleDescriptor, inputs);
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
                if (linked.getPromise().canBeViewedAsWritable(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Set<String> getLinkNames(ModelType<?> type) {
            Set<String> names = Sets.newLinkedHashSet();
            for (Map.Entry<String, ModelNodeInternal> entry : links.entrySet()) {
                if (entry.getValue().getPromise().canBeViewedAsWritable(type)) {
                    names.add(entry.getKey());
                }
            }
            return names;
        }

        @Override
        public Iterable<? extends MutableModelNode> getLinks(final ModelType<?> type) {
            return Iterables.filter(links.values(), new Predicate<ModelNodeInternal>() {
                @Override
                public boolean apply(ModelNodeInternal input) {
                    return input.getPromise().canBeViewedAsWritable(type);
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
            return linked != null && linked.getPromise().canBeViewedAsWritable(type);
        }

        @Override
        public <T> void applyToSelf(ModelActionRole type, ModelAction<T> action) {
            if (!getPath().equals(action.getSubject().getPath())) {
                throw new IllegalArgumentException(String.format("Element action reference has path (%s) which does not reference this node (%s).", action.getSubject().getPath(), getPath()));
            }
            bind(action.getSubject(), type, action, ModelPath.ROOT);
        }

        @Override
        public <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {
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
            registerListener(new ModelCreationListener() {
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
                public boolean onCreate(ModelNodeInternal node) {
                    node.applyToSelf(rules);
                    return false;
                }
            });
        }

        @Override
        public void applyToAllLinksTransitive(final ModelType<?> type, final Class<? extends RuleSource> rules) {
            registerListener(new ModelCreationListener() {
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
                public boolean onCreate(ModelNodeInternal node) {
                    node.applyToSelf(rules);
                    return false;
                }
            });
        }

        public void apply(Class<? extends RuleSource> rules, ModelPath scope) {
            Iterable<ExtractedModelRule> extractedRules = ruleExtractor.extract(rules);
            for (ExtractedModelRule extractedRule : extractedRules) {
                if (!extractedRule.getRuleDependencies().isEmpty()) {
                    throw new IllegalStateException("Rule source " + rules + " cannot have plugin dependencies (introduced by rule " + extractedRule + ")");
                }
                extractedRule.apply(DefaultModelRegistry.this, scope);
            }
        }

        @Override
        public <T> void applyToAllLinks(final ModelActionRole type, final ModelAction<T> action) {
            if (action.getSubject().getPath() != null) {
                throw new IllegalArgumentException("Linked element action reference must have null path.");
            }

            registerListener(new ModelCreationListener() {
                @Nullable
                @Override
                public ModelPath getParent() {
                    return ModelElementNode.this.getPath();
                }

                @Nullable
                @Override
                public ModelType<?> getType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onCreate(ModelNodeInternal node) {
                    bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    return false;
                }
            });
        }

        @Override
        public <T> void applyToAllLinksTransitive(final ModelActionRole type, final ModelAction<T> action) {
            if (action.getSubject().getPath() != null) {
                throw new IllegalArgumentException("Linked element action reference must have null path.");
            }

            registerListener(new ModelCreationListener() {
                @Nullable
                @Override
                public ModelPath getAncestor() {
                    return ModelElementNode.this.getPath();
                }

                @Nullable
                @Override
                public ModelType<?> getType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onCreate(ModelNodeInternal node) {
                    bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    return false;
                }
            });
        }

        @Override
        public void addReference(ModelCreator creator) {
            addNode(new ModelReferenceNode(toCreatorBinder(creator, ModelPath.ROOT), this), creator);
        }

        @Override
        public void addLink(ModelCreator creator) {
            addNode(new ModelElementNode(toCreatorBinder(creator, ModelPath.ROOT), this), creator);
        }

        private void addNode(ModelNodeInternal child, ModelCreator creator) {
            ModelPath childPath = child.getPath();
            if (!getPath().isDirectChild(childPath)) {
                throw new IllegalArgumentException(String.format("Element creator has a path (%s) which is not a child of this node (%s).", childPath, getPath()));
            }

            if (reset) {
                // Reuse child node
                registerNode(child);
                return;
            }

            ModelNodeInternal currentChild = links.get(childPath.getName());
            if (currentChild != null) {
                if (currentChild.getState() == Known) {
                    throw new DuplicateModelException(
                        String.format(
                            "Cannot create '%s' using creation rule '%s' as the rule '%s' is already registered to create this model element.",
                            childPath,
                            describe(creator.getDescriptor()),
                            describe(currentChild.getDescriptor())
                        )
                    );
                }
                throw new DuplicateModelException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as the rule '%s' has already been used to create this model element.",
                        childPath,
                        describe(creator.getDescriptor()),
                        describe(currentChild.getDescriptor())
                    )
                );
            }
            if (!isMutable()) {
                throw new IllegalStateException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as model element '%s' is no longer mutable.",
                        childPath,
                        describe(creator.getDescriptor()),
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
                    case Known:
                        node = new MakeKnown(goal.path);
                        break;
                    case Created:
                        node = new Create(goal);
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

    private class Create extends TransitionNodeToState {
        public Create(NodeAtState target) {
            super(target);
        }

        @Override
        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            // Must run the creator
            dependencies.add(new RunCreatorAction(getPath(), node.getCreatorBinder()));
            return true;
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
                if (rule.getSubjectBinding() == null || !rule.getSubjectBinding().isBound() || rule.getSubjectReference().getState() == null) {
                    // TODO - implement these cases
                    continue;
                }
                if (rule.getSubjectBinding().getNode().getPath().equals(input.path)) {
                    // Ignore future states of the input node
                    continue;
                }
                dependencies.add(graph.nodeAtState(new NodeAtState(rule.getSubjectBinding().getNode().getPath(), rule.getSubjectReference().getState())));
            }
            return true;
        }
    }

    private class ApplyActions extends TransitionNodeToState {
        private final Set<RuleBinder> seenRules = new HashSet<RuleBinder>();

        public ApplyActions(NodeAtState target) {
            super(target);
        }

        @Override
        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            // Must run each action
            for (RuleBinder binder : ruleBindings.getRulesWithSubject(target)) {
                if (seenRules.add(binder)) {
                    MutatorRuleBinder<?> mutator = Cast.uncheckedCast(binder);
                    dependencies.add(new RunModelAction(getPath(), mutator));
                }
            }
            return false;
        }

    }

    private class CloseGraph extends TransitionNodeToState {
        public CloseGraph(NodeAtState target) {
            super(target);
        }

        @Override
        boolean doCalculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (node instanceof ModelReferenceNode) {
                // Graph close the target of the reference
                ModelReferenceNode referenceNode = (ModelReferenceNode) node;
                ModelNodeInternal target = referenceNode.getTarget();
                if (target == null || target.getPath().isDescendant(getPath())) {
                    // No target, or target is an ancestor of this node, so is already being closed
                    return true;
                }
                dependencies.add(graph.nodeAtState(new NodeAtState(target.getPath(), GraphClosed)));
            } else {
                // Must graph-close each child first
                for (ModelNodeInternal child : node.getLinks()) {
                    dependencies.add(graph.nodeAtState(new NodeAtState(child.getPath(), GraphClosed)));
                }
            }
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
            dependencies.add(new TryResolvePath(parent.getTarget().getPath().child(path.getName())));
            return true;
        }

        @Override
        void apply() {
            // Rough implementation to get something to work
            ModelNodeInternal parentTarget = parent.getTarget();
            ModelNodeInternal childTarget = parentTarget.getLink(path.getName());
            ModelCreator creator = ModelCreators.of(path)
                .descriptor(parent.getDescriptor())
                .withProjection(UnmanagedModelProjection.of(Object.class))
                .build();
            ModelReferenceNode childNode = new ModelReferenceNode(toCreatorBinder(creator, ModelPath.ROOT), parent);
            childNode.setTarget(childTarget);
            registerNode(childNode);
        }
    }

    /**
     * Attempts to define the contents of the requested scope. Does not fail if not possible.
     */
    private class TryDefineScope extends ModelGoal {
        private final ModelPath scope;
        private boolean attemptedPath;

        public TryDefineScope(ModelPath scope) {
            this.scope = scope;
        }

        @Override
        public boolean isAchieved() {
            ModelNodeInternal node = modelGraph.find(scope);
            return node != null && node.getState().compareTo(SelfClosed) >= 0;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            if (!attemptedPath) {
                dependencies.add(new TryResolvePath(scope));
                attemptedPath = true;
                return false;
            }
            if (modelGraph.find(scope) != null) {
                dependencies.add(graph.nodeAtState(new NodeAtState(scope, SelfClosed)));
            }
            return true;
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
            if (binder.getSubjectBinding() != null) {
                // Shouldn't really be here. Currently this goal is used by {@link #bindAllReferences} which also expects the subject to be bound
                maybeBind(binder.getSubjectBinding(), dependencies);
            }
            for (ModelBinding binding : binder.getInputBindings()) {
                maybeBind(binding, dependencies);
            }
            return true;
        }

        private void maybeBind(ModelBinding binding, Collection<ModelGoal> dependencies) {
            if (!binding.isBound()) {
                if (binding.getPredicate().getPath() != null) {
                    dependencies.add(new TryResolvePath(binding.getPredicate().getPath()));
                } else {
                    dependencies.add(new TryDefineScope(binding.getPredicate().getScope()));
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

    private class RunCreatorAction extends RunAction {
        public RunCreatorAction(ModelPath path, CreatorRuleBinder binder) {
            super(path, binder);
        }

        @Override
        void apply() {
            CreatorRuleBinder creatorBinder = node.getCreatorBinder();
            LOGGER.debug("Running model element '{}' creator rule action {}", getPath(), creatorBinder.getDescriptor());
            doCreate(node, creatorBinder);
            node.notifyFired(creatorBinder);
        }
    }

    private class RunModelAction extends RunAction {
        private final MutatorRuleBinder<?> binder;

        public RunModelAction(ModelPath path, MutatorRuleBinder<?> binder) {
            super(path, binder);
            this.binder = binder;
        }

        @Override
        void apply() {
            LOGGER.debug("Running model element '{}' rule action {}", getPath(), binder.getDescriptor());
            fireMutation(binder);
            node.notifyFired(binder);
        }
    }
}
