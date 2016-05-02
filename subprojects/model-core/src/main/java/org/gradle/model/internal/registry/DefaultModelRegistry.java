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
import com.google.common.collect.*;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.model.ConfigurationCycleException;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.ExtractedRuleSource;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.report.unbound.UnboundRule;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.gradle.model.internal.core.ModelNode.State.*;

@NotThreadSafe
public class DefaultModelRegistry implements ModelRegistryInternal {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelRegistry.class);

    private final String projectPath;
    private final ModelGraph modelGraph;
    private final RuleBindings ruleBindings;
    private final ModelRuleExtractor ruleExtractor;
    // Use of a LinkedList for 2 reasons: `Set` proved to have a significant negative impact on performance
    // And list will see a lot of removals, which ArrayList isn't very well suited for.
    private final List<RuleBinder> unboundRules = new LinkedList<RuleBinder>();

    public DefaultModelRegistry(ModelRuleExtractor ruleExtractor, String projectPath) {
        this.ruleExtractor = ruleExtractor;
        this.projectPath = projectPath;
        ModelRegistration rootRegistration = ModelRegistrations.of(ModelPath.ROOT).descriptor("<root>").withProjection(EmptyModelProjection.INSTANCE).build();
        modelGraph = new ModelGraph(new ModelElementNode(this, rootRegistration, null));
        ruleBindings = new RuleBindings();
        transition(modelGraph.getRoot(), Created, false);
    }

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public DefaultModelRegistry register(ModelRegistration registration) {
        ModelPath path = registration.getPath();
        if (!ModelPath.ROOT.isDirectChild(path)) {
            throw new InvalidModelRuleDeclarationException(registration.getDescriptor(), "Cannot register element at '" + path + "', only top level is allowed (e.g. '" + path.getRootParent() + "')");
        }

        ModelNodeInternal root = modelGraph.getRoot();
        root.addLink(registration);
        return this;
    }

    @Override
    public void registerNode(ModelNodeInternal node, Multimap<ModelActionRole, ? extends ModelAction> actions) {
        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);
        LOGGER.debug("Project {} - Registering model element '{}' (hidden = {})", projectPath, node.getPath(), node.isHidden());
        addRuleBindings(node, actions);
        modelGraph.add(node);
        ruleBindings.nodeCreated(node);
    }

    private void addRuleBindings(ModelNodeInternal node, Multimap<ModelActionRole, ? extends ModelAction> actions) {
        for (Map.Entry<ModelActionRole, ? extends ModelAction> entry : actions.entries()) {
            ModelActionRole role = entry.getKey();
            ModelAction action = entry.getValue();
            checkNodePath(node, action);
            RuleBinder binder = bindInternal(action.getSubject(), role, action);
            node.addRegistrationActionBinder(binder);
        }
    }

    @Override
    public DefaultModelRegistry configure(ModelActionRole role, ModelAction action) {
        bindInternal(action.getSubject(), role, action);
        return this;
    }

    @Override
    public ModelRegistry configureMatching(final ModelSpec spec, final ModelActionRole role, final ModelAction action) {
        if (action.getSubject().getPath() != null) {
            throw new IllegalArgumentException("Linked element action reference must have null path.");
        }

        final ModelType<?> subjectType = action.getSubject().getType();
        registerListener(new DelegatingListener(spec) {
            @Override
            public String toString() {
                return "configure matching " + spec + " using " + action.getDescriptor();
            }

            @Override
            public void onDiscovered(ModelNodeInternal node) {
                if (node.canBeViewedAs(subjectType) && spec.matches(node)) {
                    bind(ModelReference.of(node.getPath(), subjectType), role, action);
                }
            }
        });
        return this;
    }

    @Override
    public ModelRegistry configureMatching(final ModelSpec predicate, final Class<? extends RuleSource> rules) {
        registerListener(new DelegatingListener(predicate) {
            @Override
            public String toString() {
                return "configure matching " + predicate + " apply " + rules.getSimpleName();
            }

            @Override
            public void onDiscovered(ModelNodeInternal node) {
                if (predicate.matches(node)) {
                    node.applyToSelf(rules);
                }
            }
        });
        return this;
    }

    @Override
    public ExtractedRuleSource<?> newRuleSource(Class<? extends RuleSource> rules) {
        return ruleExtractor.extract(rules);
    }

    static void checkNodePath(ModelNodeInternal node, ModelAction action) {
        if (!node.getPath().equals(action.getSubject().getPath())) {
            throw new IllegalArgumentException(String.format("Element action reference has path (%s) which does not reference this node (%s).", action.getSubject().getPath(), node.getPath()));
        }
    }

    @Override
    public <T> void bind(ModelReference<T> subject, ModelActionRole role, ModelAction mutator) {
        bindInternal(subject, role, mutator);
    }

    private <T> RuleBinder bindInternal(ModelReference<T> subject, ModelActionRole role, ModelAction mutator) {
        BindingPredicate mappedSubject = mapSubject(subject, role);
        List<BindingPredicate> mappedInputs = mapInputs(mutator.getInputs());
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
            return node.asImmutable(type, new SimpleModelRuleDescriptor(msg)).getInstance();
        }
    }

    @Override
    public ModelNode realizeNode(ModelPath path) {
        return require(path);
    }

    private void registerListener(ModelListener listener) {
        modelGraph.addListener(listener);
    }

    @Override
    public void remove(ModelPath path) {
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            return;
        }

        List<ModelNodeInternal> nodesToRemove = Lists.newArrayList();
        ensureCanRemove(node, nodesToRemove);
        Collections.reverse(nodesToRemove);

        for (ModelNodeInternal nodeToRemove : nodesToRemove) {
            modelGraph.remove(nodeToRemove);
            ruleBindings.remove(nodeToRemove);
            unboundRules.removeAll(nodeToRemove.getRegistrationActionBinders());
        }
    }

    private void ensureCanRemove(ModelNodeInternal node, List<ModelNodeInternal> nodesToRemove) {
        if (!(node instanceof ModelReferenceNode)) {
            for (ModelNodeInternal childNode : node.getLinks()) {
                ensureCanRemove(childNode, nodesToRemove);
            }
        }
        if (!Iterables.isEmpty(node.getDependents())) {
            throw new IllegalStateException(String.format("Tried to remove model '%s' but it is depended on by: '%s'",  node.getPath(), Joiner.on(", ").join(node.getDependents())));
        }
        nodesToRemove.add(node);
    }

    @Override
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
            RuleBinder[] unboundBinders = unboundRules.toArray(new RuleBinder[0]);
            for (RuleBinder binder : unboundBinders) {
                transitionTo(graph, new TryBindInputs(binder));
                newInputsBound = newInputsBound || binder.isBound();
            }
        }

        if (!unboundRules.isEmpty()) {
            SortedSet<RuleBinder> sortedBinders = new TreeSet<RuleBinder>(new Comparator<RuleBinder>() {
                @Override
                public int compare(RuleBinder o1, RuleBinder o2) {
                    return String.valueOf(o1.getDescriptor()).compareTo(String.valueOf(o2.getDescriptor()));
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
        Deque<ModelGoal> queue = new ArrayDeque<ModelGoal>();
        queue.add(targetGoal);
        List<ModelGoal> newDependencies = new ArrayList<ModelGoal>();
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
            newDependencies.clear();
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
                throw ruleCycle(dependency, Lists.newArrayList(queue));
            }
        }
    }

    private ConfigurationCycleException ruleCycle(ModelGoal brokenGoal, List<ModelGoal> queue) {
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

    @Override
    public void transition(ModelNodeInternal node, ModelNode.State desired, boolean laterOk) {
        ModelPath path = node.getPath();
        ModelNode.State state = node.getState();

        LOGGER.debug("Project {} - Transitioning model element '{}' from state {} to {}", projectPath, path, state.name(), desired.name());

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

    private void fireAction(RuleBinder boundMutator) {
        final List<ModelView<?>> inputs = toViews(boundMutator.getInputBindings(), boundMutator.getAction().getDescriptor());
        ModelBinding subjectBinding = boundMutator.getSubjectBinding();
        final ModelNodeInternal node = subjectBinding.getNode();
        final ModelAction mutator = boundMutator.getAction();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        LOGGER.debug("Project {} - Mutating {} using {}", projectPath, node.getPath(), descriptor);

        try {
            RuleContext.run(descriptor, new Runnable() {
                @Override
                public void run() {
                    mutator.execute(node, inputs);
                }
            });
        } catch (Throwable e) {
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
            ModelView<?> view = element.asImmutable(binding.getPredicate().getType(), descriptor);
            array[i++] = view;
        }
        @SuppressWarnings("unchecked") List<ModelView<?>> views = Arrays.asList(array);
        return views;
    }

    @Override
    public MutableModelNode getRoot() {
        return modelGraph.getRoot();
    }

    @Nullable
    public MutableModelNode node(ModelPath path) {
        return modelGraph.find(path);
    }

    private BindingPredicate mapSubject(ModelReference<?> subjectReference, ModelActionRole role) {
        if (!role.isSubjectViewAvailable() && !subjectReference.isUntyped()) {
            throw new IllegalStateException(String.format("Cannot bind subject '%s' to role '%s' because it is targeting a type and subject types are not yet available in that role", subjectReference, role));
        }
        return new BindingPredicate(subjectReference.atState(role.getTargetState()));
    }

    private List<BindingPredicate> mapInputs(List<? extends ModelReference<?>> inputs) {
        if (inputs.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<BindingPredicate> result = new ArrayList<BindingPredicate>(inputs.size());
        for (ModelReference<?> input : inputs) {
            if (input.getPath() == null && input.getScope() == null) {
                result.add(new BindingPredicate(input.inScope(ModelPath.ROOT)));
            } else {
                result.add(new BindingPredicate(input));
            }
        }
        return result;
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
            LOGGER.debug("Project {} - Transitioning model element '{}' to state {}.", projectPath, node.getPath(), getTargetState().name());
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

        @Override
        public String toString() {
            return "try discover and resolve path " + getPath() + ", state: " + state;
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
            ModelReferenceNode childNode = new ModelReferenceNode(DefaultModelRegistry.this, registration, parent);
            childNode.setTarget(childTarget);
            registerNode(childNode, ImmutableMultimap.<ModelActionRole, ModelAction>of());
        }

        @Override
        public String toString() {
            return "try resolve reference " + path + ", state: " + state;
        }
    }

    /**
     * Discover children that can be discovered without having to close other nodes.
     * These are nodes that only have discovery rules that don't have inputs.
     */
    private class TryDiscoverSelfDiscoveringInScope extends ModelGoal {
        private final ModelNodeInternal scopeNode;

        public TryDiscoverSelfDiscoveringInScope(ModelNodeInternal scopeNode) {
            this.scopeNode = scopeNode;
        }

        @Override
        public boolean isAchieved() {
            for (ModelNodeInternal child : scopeNode.getLinks()) {
                if (!child.isAtLeast(Discovered) && !hasInputs(new NodeAtState(child.getPath(), Discovered))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean calculateDependencies(GoalGraph graph, Collection<ModelGoal> dependencies) {
            for (ModelNodeInternal child : scopeNode.getLinks()) {
                NodeAtState target = new NodeAtState(child.getPath(), Discovered);
                if (!child.isAtLeast(Discovered) && !hasInputs(target)) {
                    dependencies.add(graph.nodeAtState(target));
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "try discover self-discovering children of scope " + scopeNode.getPath() + ", state: " + state;
        }

        private boolean hasInputs(NodeAtState target) {
            for (RuleBinder ruleBinder : ruleBindings.getRulesWithSubject(target)) {
                if (!ruleBinder.getInputBindings().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Attempts to define the contents of the requested scope. Does not fail if not possible.
     */
    private class TryDefineScopeForType extends ModelGoal {
        private final ModelPath scope;
        private final ModelType<?> typeToBind;
        private boolean attemptedPath;
        private boolean attemptedSelfDiscoveringChildren;
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
                if (child.isAtLeast(Discovered) && (child.getPromise().canBeViewedAs(typeToBind))) {
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
            ModelNodeInternal scopeNode = modelGraph.find(scope);
            if (scopeNode == null) {
                return true;
            }

            if (!attemptedSelfDiscoveringChildren) {
                dependencies.add(new TryDiscoverSelfDiscoveringInScope(scopeNode));
                attemptedSelfDiscoveringChildren = true;
                return false;
            }

            if (isAchieved()) {
                return true;
            }

            if (!attemptedCloseScope) {
                dependencies.add(graph.nodeAtState(new NodeAtState(scope, SelfClosed)));
                attemptedCloseScope = true;
                return false;
            }

            dependencies.add(new TransitionChildrenOrReference(scope, Discovered));
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
                BindingPredicate predicate = binding.getPredicate();
                if (predicate.getPath() != null) {
                    dependencies.add(new TryResolveAndDiscoverPath(predicate.getPath()));
                } else {
                    dependencies.add(new TryDefineScopeForType(predicate.getScope(), predicate.getType()));
                }
            }
        }
    }

    private class RunModelAction extends ModelNodeGoal {
        private final RuleBinder binder;
        private boolean bindInputs;

        public RunModelAction(ModelPath path, RuleBinder binder) {
            super(path);
            this.binder = binder;
        }

        @Override
        public String toString() {
            return "run action for " + binder.getSubjectBinding().getPredicate() + ", rule: " + binder.getDescriptor() + ", state: " + state;
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

        @Override
        void apply() {
            LOGGER.debug("Project {} - Running model element '{}' rule action {}", projectPath, getPath(), binder.getDescriptor());
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
            ruleBindings.nodeDiscovered(node);
            modelGraph.nodeDiscovered(node);
        }

        @Override
        public String toString() {
            return "notify discovered for " + getPath() + ", state: " + state;
        }
    }

    private abstract static class DelegatingListener extends ModelListener {
        private final ModelSpec spec;

        public DelegatingListener(ModelSpec spec) {
            this.spec = spec;
        }

        @Override
        @Nullable
        public ModelPath getPath() {
            return spec.getPath();
        }

        @Override
        @Nullable
        public ModelPath getParent() {
            return spec.getParent();
        }

        @Override
        @Nullable
        public ModelPath getAncestor() {
            return spec.getAncestor();
        }
    }
}
