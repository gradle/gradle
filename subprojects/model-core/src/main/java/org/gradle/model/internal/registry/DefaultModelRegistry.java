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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.gradle.model.internal.core.ModelNode.State.*;

@NotThreadSafe
public class DefaultModelRegistry implements ModelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelRegistry.class);

    private final ModelGraph modelGraph;
    private final ModelRuleExtractor ruleExtractor;

    private final Set<RuleBinder> unboundRules = Sets.newIdentityHashSet();
    private final List<MutatorRuleBinder<?>> pendingMutatorBinders = Lists.newLinkedList();
    private final LinkedHashMap<ModelRule, ModelBinding> rulesWithInputsBeingClosed = Maps.newLinkedHashMap();

    boolean reset;

    public DefaultModelRegistry(ModelRuleExtractor ruleExtractor) {
        this.ruleExtractor = ruleExtractor;
        ModelCreator rootCreator = ModelCreators.of(ModelPath.ROOT, BiActions.doNothing()).descriptor("<root>").withProjection(EmptyModelProjection.INSTANCE).build();
        modelGraph = new ModelGraph(new ModelElementNode(toCreatorBinder(rootCreator, ModelPath.ROOT), null));
        modelGraph.getRoot().setState(Created);
    }

    private static String toString(ModelRuleDescriptor descriptor) {
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
        registerNode(root, new ModelElementNode(toCreatorBinder(creator, ModelPath.ROOT), root));
        return this;
    }

    private CreatorRuleBinder toCreatorBinder(ModelCreator creator, ModelPath scope) {
        List<ModelReference<?>> inputs = inputsToScope(creator.getInputs(), scope);
        return new CreatorRuleBinder(creator, inputs, unboundRules);
    }

    private ModelNodeInternal registerNode(ModelNodeInternal parent, ModelNodeInternal child) {
        if (reset) {
            return child;
        }

        ModelCreator creator = child.getCreatorBinder().getCreator();
        ModelPath path = child.getPath();

        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);

        ModelNodeInternal node = modelGraph.find(path);
        if (node != null) {
            if (node.getState() == Known) {
                throw new DuplicateModelException(
                    String.format(
                        "Cannot create '%s' using creation rule '%s' as the rule '%s' is already registered to create this model element.",
                        path,
                        toString(creator.getDescriptor()),
                        toString(node.getDescriptor())
                    )
                );
            }
            throw new DuplicateModelException(
                String.format(
                    "Cannot create '%s' using creation rule '%s' as the rule '%s' has already been used to create this model element.",
                    path,
                    toString(creator.getDescriptor()),
                    toString(node.getDescriptor())
                )
            );
        }
        if (!parent.isMutable()) {
            throw new IllegalStateException(
                String.format(
                    "Cannot create '%s' using creation rule '%s' as model element '%s' is no longer mutable.",
                    path,
                    toString(creator.getDescriptor()),
                    parent.getPath()
                )
            );
        }

        node = parent.addLink(child);
        modelGraph.add(node);
        return node;
    }

    @Override
    public <T> DefaultModelRegistry configure(ModelActionRole role, ModelAction<T> action) {
        bind(action.getSubject(), role, action, ModelPath.ROOT);
        return this;
    }

    @Override
    public ModelRegistry apply(Class<? extends RuleSource> rules) {
        modelGraph.getRoot().applyToSelf(rules);
        return this;
    }

    private <T> void bind(ModelActionRole role, ModelAction<T> mutator, ModelPath scope) {
        bind(mutator.getSubject(), role, mutator, scope);
    }

    private <T> void bind(ModelReference<T> subject, ModelActionRole role, ModelAction<T> mutator, ModelPath scope) {
        if (reset) {
            return;
        }
        ModelReference<T> mappedSubject = subjectToScope(subject, scope);
        List<ModelReference<?>> mappedInputs = inputsToScope(mutator.getInputs(), scope);
        MutatorRuleBinder<T> binder = new MutatorRuleBinder<T>(mappedSubject, mappedInputs, role, mutator, unboundRules);
        pendingMutatorBinders.add(binder);
    }

    private void flushPendingMutatorBinders() {
        Iterator<MutatorRuleBinder<?>> iterator = pendingMutatorBinders.iterator();
        while (iterator.hasNext()) {
            MutatorRuleBinder<?> binder = iterator.next();
            iterator.remove();
            registerListener(binder.getSubjectBinding());
        }
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
        return this;
    }

    private ModelNode selfCloseAncestryAndSelf(ModelPath path) {
        ModelPath parent = path.getParent();
        if (parent != null) {
            if (selfCloseAncestryAndSelf(parent) == null) {
                return null;
            }
        }
        return atStateOrLater(path, SelfClosed);
    }

    public void bindAllReferences() throws UnboundModelRulesException {
        flushPendingMutatorBinders();
        if (unboundRules.isEmpty()) {
            return;
        }

        boolean newInputsBound = true;
        while (!unboundRules.isEmpty() && newInputsBound) {
            newInputsBound = false;
            RuleBinder[] unboundBinders = unboundRules.toArray(new RuleBinder[unboundRules.size()]);
            for (RuleBinder binder : unboundBinders) {
                tryForceBind(binder);
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
        ModelNodeInternal node = modelGraph.find(path);
        if (node == null) {
            return null;
        }
        close(node);
        return node;
    }

    private void close(ModelNodeInternal node) {
        transition(node, GraphClosed, false);
    }

    /**
     * Attempts to achieve the given goal.
     */
    // TODO - reuse graph, discard state once not required
    private void transitionTo(GoalGraph goalGraph, GoalGraphNode targetGoal) {
        LinkedList<GoalGraphNode> queue = new LinkedList<GoalGraphNode>();
        queue.add(targetGoal);
        while (!queue.isEmpty()) {
            GoalGraphNode goal = queue.getFirst();

            if (goal.state == GoalGraphNode.State.Achieved) {
                // Already reached this goal
                queue.removeFirst();
                continue;
            }

            if (goal.state == GoalGraphNode.State.VisitingDependencies) {
                // All dependencies visited
                goal.apply();
                goal.state = GoalGraphNode.State.Achieved;
                queue.removeFirst();
                continue;
            }

            if (goal.state == GoalGraphNode.State.NotSeen) {
                ModelNodeInternal node = modelGraph.find(goal.getPath());
                if (node != null) {
                    goal.node = node;
                    if (goal.isAchieved()) {
                        // Goal has previously been reached
                        goal.state = GoalGraphNode.State.Achieved;
                        queue.removeFirst();
                        continue;
                    }
                }

                // Attach the predecessors, which are the goals that must be achieved before the dependencies of the target goal can be calculated
                goal.calculatePredecessors(goalGraph);
                goal.state = GoalGraphNode.State.VisitingPredecessor;
            } else if (goal.state == GoalGraphNode.State.VisitingPredecessor) {
                ModelNodeInternal node = modelGraph.find(goal.getPath());
                if (node == null) {
                    throw new IllegalStateException(String.format("Model element '%s' does not exist.", goal.getPath()));
                }
                goal.node = node;

                // Attach the dependencies, which are the goals that must be achieved before the goal can be applied
                goal.calculateDependencies(goalGraph);
                goal.state = GoalGraphNode.State.VisitingDependencies;
            }
            for (int i = goal.dependencies.size() - 1; i >= 0; i--) {
                // TODO - only queue new dependencies
                // TODO - check for cycles
                queue.addFirst(goal.dependencies.get(i));
            }
        }
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
        transitionTo(goalGraph, goalGraph.nodeAtState(new NodeIsAtState(node.getPath(), desired)));
    }

    private void forceBindReference(ModelBinding binding) {
        if (!binding.isBound()) {
            ModelPath path = binding.getReference().getPath();
            if (path == null) {
                selfCloseAncestryAndSelf(binding.getReference().getScope());
            } else {
                selfCloseAncestryAndSelf(path.getParent());
            }
        }
    }

    private void tryForceBind(RuleBinder binder) {
        if (binder.isBound()) {
            return;
        }

        for (ModelBinding binding : binder.getInputBindings()) {
            registerListener(binding);
        }

        if (binder.getSubjectBinding() != null) {
            forceBindReference(binder.getSubjectBinding());
        }

        for (int i = 0; i < binder.getInputBindings().size(); i++) {
            forceBindReference(binder.getInputBindings().get(i));
        }
    }

    private <T> ModelView<? extends T> assertView(ModelNodeInternal node, ModelType<T> targetType, @Nullable ModelRuleDescriptor descriptor, String msg, Object... msgArgs) {
        ModelAdapter adapter = node.getAdapter();
        ModelView<? extends T> view = adapter.asReadOnly(targetType, node, descriptor);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model node '" + node.getPath().toString() + "' is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return view;
        }
    }

    private <T> ModelView<? extends T> assertView(ModelNodeInternal node, ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelView<?>> inputs) {
        ModelAdapter adapter = node.getAdapter();
        ModelView<? extends T> view = adapter.asWritable(reference.getType(), node, sourceDescriptor, inputs);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Cannot project model element " + node.getPath() + " to writable type '" + reference.getType() + "' for rule " + sourceDescriptor);
        } else {
            return view;
        }
    }

    private ModelNodeInternal doCreate(ModelNodeInternal node, CreatorRuleBinder boundCreator) {
        ModelCreator creator = boundCreator.getCreator();
        List<ModelView<?>> views = toViews(boundCreator.getInputBindings(), boundCreator.getCreator());

        LOGGER.debug("Creating {} using {}", node.getPath(), creator.getDescriptor());

        try {
            creator.create(node, views);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getDescriptor(), e);
        }

        return node;
    }

    private <T> void fireMutation(MutatorRuleBinder<T> boundMutator) {
        List<ModelView<?>> inputs = toViews(boundMutator.getInputBindings(), boundMutator.getAction());

        ModelNodeInternal node = boundMutator.getSubjectBinding().getNode();
        ModelAction<T> mutator = boundMutator.getAction();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        LOGGER.debug("Mutating {} using {}", node.getPath(), mutator.getDescriptor());

        ModelView<? extends T> view = assertView(node, boundMutator.getSubjectReference(), descriptor, inputs);
        try {
            mutator.execute(node, view.getInstance(), inputs);
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
            closeRuleBinding(modelRule, binding);
            ModelPath path = binding.getNode().getPath();
            ModelNodeInternal element = require(path);
            ModelView<?> view = assertView(element, binding.getReference().getType(), modelRule.getDescriptor(), "toViews");
            array[i++] = view;
        }
        @SuppressWarnings("unchecked") List<ModelView<?>> views = Arrays.asList(array);
        return views;
    }

    private void closeRuleBinding(ModelRule modelRule, ModelBinding binding) {
        if (rulesWithInputsBeingClosed.containsKey(modelRule)) {
            throw ruleCycle(modelRule);
        }
        rulesWithInputsBeingClosed.put(modelRule, binding);
        try {
            close(binding.getNode());
        } finally {
            rulesWithInputsBeingClosed.remove(modelRule);
        }
    }

    private ConfigurationCycleException ruleCycle(ModelRule cycleStartRule) {
        boolean cycleStartFound = false;
        String indent = "  ";
        StringBuilder prefix = new StringBuilder(indent);
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        writer.println("A cycle has been detected in model rule dependencies. References forming the cycle:");

        for (Map.Entry<ModelRule, ModelBinding> ruleInputInClosing : rulesWithInputsBeingClosed.entrySet()) {
            ModelRule rule = ruleInputInClosing.getKey();
            ModelRuleDescriptor ruleDescriptor = rule.getDescriptor();
            ModelBinding binding = ruleInputInClosing.getValue();
            if (cycleStartFound) {
                reportRuleInputBeingClosed(indent, prefix, writer, ruleDescriptor, binding);
            } else {
                if (rule.equals(cycleStartRule)) {
                    cycleStartFound = true;
                    reportRuleInputBeingClosed(indent, prefix, writer, ruleDescriptor, binding);
                }
            }
        }
        writer.print(cycleStartRule.getDescriptor().toString());

        return new ConfigurationCycleException(out.toString());
    }

    private void reportRuleInputBeingClosed(String indent, StringBuilder prefix, PrintWriter writer, ModelRuleDescriptor ruleDescriptor, ModelBinding binding) {
        writer.print(ruleDescriptor.toString());
        String referenceDescription = binding.getReference().getDescription();
        if (referenceDescription != null) {
            writer.print(" ");
            writer.print(referenceDescription);
        }
        writer.print(" (path: ");
        writer.print(binding.getNode().getPath().toString());
        writer.print(")");
        writer.println();
        writer.print(prefix);
        writer.print("\\--- ");
        prefix.append(indent);
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

    private <T> ModelReference<T> subjectToScope(ModelReference<T> subjectReference, ModelPath scope) {
        if (subjectReference.getPath() == null) {
            return subjectReference.withScope(scope);
        }
        return subjectReference.withPath(scope.descendant(subjectReference.getPath()));
    }

    private List<ModelReference<?>> inputsToScope(List<ModelReference<?>> inputs, ModelPath scope) {
        if (inputs.isEmpty()) {
            return inputs;
        }
        ArrayList<ModelReference<?>> result = new ArrayList<ModelReference<?>>(inputs.size());
        for (ModelReference<?> input : inputs) {
            if (input.getPath() != null) {
                result.add(input.withPath(scope.descendant(input.getPath())));
            } else {
                result.add(input.withScope(ModelPath.ROOT));
            }
        }
        return result;
    }

    private class ModelReferenceNode extends ModelNodeInternal {
        private ModelNodeInternal target;

        public ModelReferenceNode(CreatorRuleBinder creatorBinder) {
            super(creatorBinder);
        }

        @Override
        public ModelNodeInternal getTarget() {
            return target;
        }

        @Override
        public void setTarget(ModelNode target) {
            this.target = (ModelNodeInternal) target;
        }

        @Override
        public ModelNodeInternal addLink(ModelNodeInternal node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLink(ModelCreator creator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addReference(ModelCreator creator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeLink(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void applyToSelf(ModelActionRole type, ModelAction<T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void applyToAllLinks(ModelActionRole type, ModelAction<T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void applyToAllLinksTransitive(ModelActionRole type, ModelAction<T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyToLink(String name, Class<? extends RuleSource> rules) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void applyToLinks(Class<T> type, Class<? extends RuleSource> rules) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyToSelf(Class<? extends RuleSource> rules) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLinkCount(ModelType<?> type) {
            return 0;
        }

        @Override
        public Set<String> getLinkNames(ModelType<?> type) {
            return Collections.emptySet();
        }

        @Nullable
        @Override
        public MutableModelNode getLink(String name) {
            return null;
        }

        @Override
        public Iterable<? extends ModelNodeInternal> getLinks() {
            return Collections.emptySet();
        }

        @Override
        public Iterable<? extends MutableModelNode> getLinks(ModelType<?> type) {
            return Collections.emptySet();
        }

        @Override
        public boolean hasLink(String name, ModelType<?> type) {
            return false;
        }

        @Override
        public boolean hasLink(String name) {
            return false;
        }

        @Override
        public <T> T getPrivateData(ModelType<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void setPrivateData(ModelType<? super T> type, T object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getPrivateData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureUsable() {
        }

        @Override
        public MutableModelNode getParent() {
            return null;
        }
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
        public ModelNodeInternal addLink(ModelNodeInternal node) {
            links.put(node.getPath().getName(), node);
            return node;
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
        public <T> void applyToLinks(Class<T> type, final Class<? extends RuleSource> rules) {
            final ModelType<T> modelType = ModelType.of(type);
            registerListener(new ModelCreationListener() {
                @Nullable
                @Override
                public ModelPath getParent() {
                    return getPath();
                }

                @Nullable
                @Override
                public ModelType<?> getType() {
                    return modelType;
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

                if (extractedRule.getType().equals(ExtractedModelRule.Type.CREATOR)) {
                    if (scope.equals(ModelPath.ROOT)) {
                        DefaultModelRegistry.this.create(extractedRule.getCreator());
                    } else {
                        throw new InvalidModelRuleDeclarationException("Rule " + extractedRule.getCreator().getDescriptor() + " cannot be applied at the scope of model element " + scope + " as creation rules cannot be used when applying rule sources to particular elements");
                    }
                } else if (extractedRule.getType().equals(ExtractedModelRule.Type.ACTION)) {
                    bind(extractedRule.getActionRole(), extractedRule.getAction(), scope);
                } else {
                    throw new IllegalStateException("unexpected extracted rule type: " + extractedRule.getType());
                }
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
                public ModelType<?> getType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onCreate(ModelNodeInternal node) {
                    if (ModelElementNode.this.getPath().isDescendant(node.getPath())) {
                        bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    }
                    return false;
                }
            });
        }

        @Override
        public void addReference(ModelCreator creator) {
            if (!getPath().isDirectChild(creator.getPath())) {
                throw new IllegalArgumentException(String.format("Reference element creator has a path (%s) which is not a child of this node (%s).", creator.getPath(), getPath()));
            }
            registerNode(this, new ModelReferenceNode(toCreatorBinder(creator, ModelPath.ROOT)));
        }

        @Override
        public void addLink(ModelCreator creator) {
            if (!getPath().isDirectChild(creator.getPath())) {
                throw new IllegalArgumentException(String.format("Linked element creator has a path (%s) which is not a child of this node (%s).", creator.getPath(), getPath()));
            }
            registerNode(this, new ModelElementNode(toCreatorBinder(creator, ModelPath.ROOT), this));
        }

        @Override
        public void removeLink(String name) {
            if (links.remove(name) != null) {
                remove(getPath().child(name));
            }
        }

        @Override
        public ModelNodeInternal getTarget() {
            return this;
        }

        @Override
        public void setTarget(ModelNode target) {
            throw new UnsupportedOperationException(String.format("This node (%s) is not a reference to another node.", getPath()));
        }

        @Override
        public void ensureUsable() {
            transition(this, Initialized, true);
        }
    }

    private class GoalGraph {
        private final Map<NodeIsAtState, GoalGraphNode> nodeStates = new HashMap<NodeIsAtState, GoalGraphNode>();

        public GoalGraphNode nodeAtState(NodeIsAtState goal) {
            GoalGraphNode node = nodeStates.get(goal);
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

    private abstract static class GoalGraphNode {
        enum State {
            NotSeen,
            VisitingPredecessor,
            VisitingDependencies,
            Achieved,
        }
        public final ModelPath target;
        public final List<GoalGraphNode> dependencies = new ArrayList<GoalGraphNode>();
        public State state = State.NotSeen;
        public ModelNodeInternal node;

        public GoalGraphNode(ModelPath target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ", target: " + target.toString();
        }

        ModelPath getPath() {
            return target;
        }

        /**
         * Predecessors are those goals that must be achieved before the dependencies of this goal can be calculated.
         */
        void calculatePredecessors(GoalGraph graph) {
        }

        /**
         * Dependencies are those goals that must be achieved before the action of this goal can be applied.
         */
        void calculateDependencies(GoalGraph graph) {
        }

        /**
         * Determines whether the goal has already been achieved. Invoked only if target node exists, and if true is returned the dependencies of this
         * goal are not traversed and the action not applied.
         */
        public boolean isAchieved() {
            return false;
        }

        /**
         * Applies the action of this goal.
         */
        void apply() {
        }
    }

    private static class MakeKnown extends GoalGraphNode {
        public MakeKnown(ModelPath path) {
            super(path);
        }

        @Override
        public boolean isAchieved() {
            return node != null;
        }

        @Override
        void calculatePredecessors(GoalGraph goalGraph) {
            ModelPath parent = getPath().getParent();
            if (parent != null) {
                // TODO - should be >= self closed
                dependencies.add(goalGraph.nodeAtState(new NodeIsAtState(parent, SelfClosed)));
            }
        }
    }

    private abstract class TransitionToState extends GoalGraphNode {
        private final ModelNode.State targetState;

        public TransitionToState(NodeIsAtState target) {
            super(target.path);
            targetState = target.state;
        }

        public ModelNode.State getTargetState() {
            return targetState;
        }

        @Override
        public boolean isAchieved() {
            return node.getState().compareTo(getTargetState()) >= 0;
        }

        @Override
        final void apply() {
            if (!node.getState().equals(getTargetState().previous())) {
                throw new IllegalStateException(String.format("Cannot transition model element '%s' to state %s as it is already at state %s.", node.getPath(), getTargetState(), node.getState()));
            }
            LOGGER.debug("Transitioning model element '{}' to state {}.", node.getPath(), getTargetState().name());
            doApply(node);
            node.setState(getTargetState());
        }

        abstract void doApply(ModelNodeInternal node);
    }

    private class Create extends TransitionToState {
        public Create(NodeIsAtState target) {
            super(target);
        }

        @Override
        void calculatePredecessors(GoalGraph goalGraph) {
            dependencies.add(goalGraph.nodeAtState(new NodeIsAtState(getPath(), Known)));
        }

        @Override
        void calculateDependencies(GoalGraph graph) {
            dependencies.add(new BindInputs(getPath(), node.getCreatorBinder()));
        }

        @Override
        void doApply(ModelNodeInternal node) {
            CreatorRuleBinder creatorBinder = node.getCreatorBinder();
            doCreate(node, creatorBinder);
            node.notifyFired(creatorBinder);
        }
    }

    private class ApplyActions extends TransitionToState {
        public ApplyActions(NodeIsAtState target) {
            super(target);
        }

        @Override
        void calculatePredecessors(GoalGraph goalGraph) {
            dependencies.add(goalGraph.nodeAtState(new NodeIsAtState(getPath(), getTargetState().previous())));
        }

        @Override
        void calculateDependencies(GoalGraph graph) {
            flushPendingMutatorBinders();
            for (MutatorRuleBinder<?> binder : node.getMutatorBinders(getTargetState().role())) {
                dependencies.add(new BindInputs(getPath(), binder));
            }
        }

        @Override
        void doApply(ModelNodeInternal node) {
            for (MutatorRuleBinder<?> binder : node.getMutatorBinders(getTargetState().role())) {
                fireMutation(binder);
                node.notifyFired(binder);
                flushPendingMutatorBinders();
            }
        }
    }

    private class CloseGraph extends TransitionToState {
        public CloseGraph(NodeIsAtState target) {
            super(target);
        }

        @Override
        void calculatePredecessors(GoalGraph goalGraph) {
            dependencies.add(goalGraph.nodeAtState(new NodeIsAtState(getPath(), SelfClosed)));
        }

        @Override
        void calculateDependencies(GoalGraph goalGraph) {
            for (ModelNodeInternal child : node.getLinks()) {
                dependencies.add(goalGraph.nodeAtState(new NodeIsAtState(child.getPath(), GraphClosed)));
            }
        }

        @Override
        void doApply(ModelNodeInternal node) {
            // Nothing to do
        }
    }

    // TODO - merge with RuleBinder
    private class BindInputs extends GoalGraphNode {
        private final RuleBinder binder;

        public BindInputs(ModelPath path, RuleBinder binder) {
            super(path);
            this.binder = binder;
        }

        @Override
        void calculatePredecessors(GoalGraph goalGraph) {
        }

        @Override
        void apply() {
            LOGGER.debug("Binding inputs model element '{}' rule {}", getPath(), binder.getDescriptor());
            tryForceBind(binder);
            if (!binder.isBound()) {
                throw unbound(Collections.singleton(binder));
            }
        }
    }

    private static class NodeIsAtState implements Comparable<NodeIsAtState> {
        public final ModelPath path;
        public final ModelNode.State state;

        public NodeIsAtState(ModelPath path, ModelNode.State state) {
            this.path = path;
            this.state = state;
        }

        @Override
        public String toString() {
            return "node " + path + " at state " + state;
        }

        @Override
        public int compareTo(NodeIsAtState other) {
            int diff = path.compareTo(other.path);
            if (diff != 0) {
                return diff;
            }
            return state.compareTo(other.state);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            NodeIsAtState other = (NodeIsAtState) obj;
            return path.equals(other.path) && state.equals(other.state);
        }

        @Override
        public int hashCode() {
            return path.hashCode() ^ state.hashCode();
        }
    }
}
