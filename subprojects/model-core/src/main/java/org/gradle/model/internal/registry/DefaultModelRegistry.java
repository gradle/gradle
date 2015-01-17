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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Actions;
import org.gradle.internal.Transformers;
import org.gradle.model.InvalidModelRuleException;
import org.gradle.model.ModelRuleBindingException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.report.AmbiguousBindingReporter;
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter;
import org.gradle.model.internal.report.unbound.UnboundRule;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.gradle.model.internal.core.ModelActionRole.*;
import static org.gradle.model.internal.core.ModelNode.State.*;

@NotThreadSafe
public class DefaultModelRegistry implements ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelRegistry.class);

    /*
                Things we aren't doing and should:

                - Detecting cycles between config rules
                - Detecting dangling, unbound, rules
                - Detecting model elements with no object at their parent path
                - Detecting mutation rules registering parent model
                - Detecting a rule binding the same input twice (maybe that's ok)
                - Detecting a rule trying to bind the same element to mutate and to read
             */
    private final ModelGraph modelGraph;

    private final Map<ModelPath, BoundModelCreator> creators = Maps.newHashMap();
    private final Multimap<MutationKey, BoundModelMutator<?>> actions = ArrayListMultimap.create();
    private final Multimap<ModelPath, List<ModelPath>> usedActions = ArrayListMultimap.create();

    private final List<RuleBinder<?>> binders = Lists.newLinkedList();

    private final ModelRuleSourceApplicator modelRuleSourceApplicator;
    private final PluginClassApplicator pluginClassApplicator;

    public DefaultModelRegistry(ModelRuleSourceApplicator modelRuleSourceApplicator, PluginClassApplicator pluginClassApplicator) {
        this.modelRuleSourceApplicator = modelRuleSourceApplicator;
        this.pluginClassApplicator = pluginClassApplicator;
        EmptyModelProjection projection = new EmptyModelProjection();
        modelGraph = new ModelGraph(new NodeWrapper(ModelPath.ROOT, new SimpleModelRuleDescriptor("<root>"), projection, projection));
        modelGraph.getRoot().setState(Created);
    }

    private static String toString(ModelRuleDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    public DefaultModelRegistry create(ModelCreator creator, ModelPath scope) {
        ModelPath path = creator.getPath();
        if (!scope.equals(ModelPath.ROOT)) {
            throw new IllegalStateException("Creator in scope " + scope + " not supported, must be root");
        }
        if (!ModelPath.ROOT.isDirectChild(path)) {
            throw new IllegalStateException("Creator at path " + path + " not supported, must be top level");
        }

        doCreate(modelGraph.getRoot(), creator);
        return this;
    }

    private ModelNodeData doCreate(ModelNodeData parent, ModelCreator creator) {
        ModelPath path = creator.getPath();

        // Disabled before 2.3 release due to not wanting to validate task names (which may contain invalid chars), at least not yet
        // ModelPath.validateName(name);

        ModelNodeData node = modelGraph.find(path);
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

        node = parent.addLink(new NodeWrapper(path, creator.getDescriptor(), creator.getPromise(), creator.getAdapter()));
        modelGraph.add(node);
        bind(creator);
        return node;
    }

    @Override
    public <T> DefaultModelRegistry apply(ModelActionRole role, ModelAction<T> action, ModelPath scope) {
        bind(action.getSubject(), role, action, scope);
        return this;
    }

    private void bind(final ModelCreator creator) {
        final RuleBinder<Void> binder = bind(null, creator.getInputs(), creator.getDescriptor(), ModelPath.ROOT, new Action<RuleBinder<Void>>() {
            public void execute(RuleBinder<Void> ruleBinding) {
                BoundModelCreator boundCreator = new BoundModelCreator(creator, ruleBinding.getInputBindings());
                creators.put(creator.getPath(), boundCreator);
            }
        });

        bindInputs(binder, ModelPath.ROOT);
    }

    @SuppressWarnings("unchecked")
    private <T> RuleBinder<T> bind(ModelReference<T> subject, List<? extends ModelReference<?>> inputs, ModelRuleDescriptor descriptor, ModelPath scope, Action<? super RuleBinder<T>> onBind) {
        RuleBinder<T> binder = new RuleBinder<T>(subject, inputs, descriptor, scope, Actions.composite(new Action<RuleBinder<T>>() {
            public void execute(RuleBinder<T> binder) {
                // TODO this is going to run even if we never added the binder to the bindings (inefficient)
                binders.remove(binder);
            }
        }, onBind));

        if (!binder.isBound()) {
            binders.add(binder);
        }

        return binder;
    }

    private <T> void bind(ModelReference<T> subject, final ModelActionRole type, final ModelAction<T> mutator, ModelPath scope) {
        final RuleBinder<T> binder = bind(subject, mutator.getInputs(), mutator.getDescriptor(), scope, new Action<RuleBinder<T>>() {
            public void execute(RuleBinder<T> ruleBinder) {
                BoundModelMutator<T> boundMutator = new BoundModelMutator<T>(mutator, ruleBinder.getSubjectBinding(), ruleBinder.getInputBindings());
                ModelPath path = boundMutator.getSubject().getPath();
                ModelNodeData subject = modelGraph.find(path);
                if (!subject.canApply(type)) {
                    throw new IllegalStateException(String.format(
                            "Cannot add %s rule '%s' for model element '%s' when element is in state %s.",
                            type,
                            boundMutator.getMutator().getDescriptor(),
                            path,
                            subject.getState()
                    ));
                }
                actions.put(new MutationKey(path, type), boundMutator);
            }
        });

        registerListener(listener(binder.getDescriptor(), binder.getSubjectReference(), scope, true, new Action<ModelPath>() {
            public void execute(ModelPath modelPath) {
                binder.bindSubject(modelPath);
            }
        }));

        bindInputs(binder, scope);
    }

    private void bindInputs(final RuleBinder<?> binder, ModelPath scope) {
        List<? extends ModelReference<?>> inputReferences = binder.getInputReferences();
        for (int i = 0; i < inputReferences.size(); i++) {
            final int finalI = i;
            ModelReference<?> input = inputReferences.get(i);
            ModelPath effectiveScope = input.getPath() != null ? scope : ModelPath.ROOT;
            registerListener(listener(binder.getDescriptor(), input, effectiveScope, false, new Action<ModelPath>() {
                public void execute(ModelPath modelPath) {
                    binder.bindInput(finalI, modelPath);
                }
            }));
        }
    }

    private ModelCreationListener listener(ModelRuleDescriptor descriptor, ModelReference<?> reference, ModelPath scope, boolean writable, Action<? super ModelPath> bindAction) {
        if (reference.getPath() != null) {
            return new PathBinderCreationListener(descriptor, reference, scope, writable, bindAction);
        }
        return new OneOfTypeBinderCreationListener(descriptor, reference, scope, writable, bindAction);
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
        ModelNodeData node = modelGraph.find(path);
        if (node == null) {
            return null;
        }
        transition(node, state, laterOk);
        return node;
    }

    public <T> T find(ModelPath path, ModelType<T> type) {
        return toType(type, get(path), "find(ModelPath, ModelType)");
    }

    private <T> T toType(ModelType<T> type, ModelNodeData node, String msg) {
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
        if (isDependedOn(path)) {
            throw new RuntimeException("Tried to remove model " + path + " but it is depended on by other model elements");
        }

        creators.remove(path);
        modelGraph.remove(path);
    }

    public void validate() throws UnboundModelRulesException {
        if (binders.isEmpty()) {
            return;
        }

        // Some rules may not have bound because their references are to nested properties
        // This can happen, for example, if the reference is to a property of a managed model element
        // Here, we look for such “non root” bindings where we know the parent exists.
        // If we find such unbound references, we force the creation of the parent to try and bind the nested reference.

        // TODO if we push more knowledge of nested properties up out of constructors, we can potentially bind such references without creating the parent chain.

        while (!binders.isEmpty()) {
            Iterable<ModelPath> unboundPaths = Iterables.concat(Iterables.transform(binders, new Function<RuleBinder<?>, Iterable<ModelPath>>() {
                public Iterable<ModelPath> apply(RuleBinder<?> input) {
                    return input.getUnboundPaths();
                }
            }));

            ModelPath unboundTopLevelModelPath = Iterables.find(unboundPaths, new Predicate<ModelPath>() {
                public boolean apply(ModelPath input) {
                    return input.getRootParent() != null;
                }
            }, null);

            if (unboundTopLevelModelPath != null) {
                ModelPath rootParent = unboundTopLevelModelPath.getRootParent();
                if (creators.containsKey(rootParent)) {
                    get(rootParent);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        if (!binders.isEmpty()) {
            ModelPathSuggestionProvider suggestionsProvider = new ModelPathSuggestionProvider(Iterables.concat(modelGraph.getFlattened().keySet(), creators.keySet()));
            List<? extends UnboundRule> unboundRules = new UnboundRulesProcessor(binders, suggestionsProvider).process();
            throw new UnboundModelRulesException(unboundRules);
        }
    }

    // TODO - this needs to consider partially bound rules
    private boolean isDependedOn(ModelPath candidate) {
        Transformer<Iterable<ModelPath>, BoundModelMutator<?>> extractInputPaths = new Transformer<Iterable<ModelPath>, BoundModelMutator<?>>() {
            public Iterable<ModelPath> transform(BoundModelMutator<?> original) {
                return Iterables.transform(original.getInputs(), new Function<ModelBinding<?>, ModelPath>() {
                    @Nullable
                    public ModelPath apply(ModelBinding<?> input) {
                        return input.getPath();
                    }
                });
            }
        };

        Transformer<List<ModelPath>, List<ModelPath>> passThrough = Transformers.noOpTransformer();

        return hasModelPath(candidate, actions.values(), extractInputPaths)
                || hasModelPath(candidate, usedActions.values(), passThrough);
    }

    private <T> boolean hasModelPath(ModelPath candidate, Iterable<T> things, Transformer<? extends Iterable<ModelPath>, T> transformer) {
        for (T thing : things) {
            for (ModelPath path : transformer.transform(thing)) {
                if (path.equals(candidate)) {
                    return true;
                }
            }
        }

        return false;
    }

    private ModelNodeData require(ModelPath path) {
        ModelNodeData node = get(path);
        if (node == null) {
            throw new IllegalStateException("No model node at '" + path + "'");
        }
        return node;
    }

    @Override
    public ModelNode.State state(ModelPath path) {
        ModelNodeData modelNode = modelGraph.find(path);
        return modelNode == null ? null : modelNode.getState();
    }

    private ModelNodeData get(ModelPath path) {
        ModelNodeData node = modelGraph.find(path);
        if (node == null) {
            return null;
        }
        close(node);
        return node;
    }

    private void close(ModelNodeData node) {
        transition(node, GraphClosed, false);
    }

    private void transition(ModelNodeData node, ModelNode.State desired, boolean laterOk) {
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

        if (state == Known && desired.ordinal() >= Created.ordinal()) {
            BoundModelCreator creator = creators.remove(path);
            if (creator == null) {
                // Unbound creator - should give better error message here
                throw new IllegalStateException("Don't know how to create model element at '" + path + "'");
            }
            doCreate(node, creator);
            node.setState(Created);

            if (desired == Created) {
                return;
            }
        }

        if (!fireMutations(node, path, state, Defaults, DefaultsApplied, desired)) {
            return;
        }
        if (!fireMutations(node, path, state, Initialize, Initialized, desired)) {
            return;
        }
        if (!fireMutations(node, path, state, Mutate, Mutated, desired)) {
            return;
        }
        if (!fireMutations(node, path, state, Finalize, Finalized, desired)) {
            return;
        }
        if (!fireMutations(node, path, state, Validate, SelfClosed, desired)) {
            return;
        }

        if (desired.ordinal() >= GraphClosed.ordinal()) {
            for (ModelNodeData child : node.getLinks().values()) {
                close(child);
            }
            node.setState(GraphClosed);
        }

        LOGGER.debug("Finished transitioning model element {} from state {} to {}", path, state.name(), desired.name());
    }

    // NOTE: this should only be called from transition() as implicit logic is shared
    private boolean fireMutations(ModelNodeData node, ModelPath path, ModelNode.State originalState, ModelActionRole type, ModelNode.State to, ModelNode.State desired) {
        ModelNode.State nodeState = node.getState();
        if (nodeState.ordinal() >= to.ordinal()) {
            return nodeState.ordinal() < desired.ordinal();
        }

        Collection<BoundModelMutator<?>> mutators = this.actions.removeAll(new MutationKey(path, type));
        for (BoundModelMutator<?> mutator : mutators) {
            fireMutation(node, mutator);
            List<ModelPath> inputPaths = Lists.transform(mutator.getInputs(), new Function<ModelBinding<?>, ModelPath>() {
                @Nullable
                public ModelPath apply(ModelBinding<?> input) {
                    return input.getPath();
                }
            });
            usedActions.put(path, inputPaths);
        }

        node.setState(to);

        if (to == desired) {
            LOGGER.debug("Finished transitioning model element {} from state {} to {}", path, originalState.name(), desired.name());
            return false;
        } else {
            return true;
        }
    }

    private <T> ModelView<? extends T> assertView(ModelNodeData node, ModelType<T> targetType, @Nullable ModelRuleDescriptor descriptor, String msg, Object... msgArgs) {
        ModelAdapter adapter = node.getAdapter();
        ModelView<? extends T> view = adapter.asReadOnly(targetType, node, descriptor, modelRuleSourceApplicator, this, pluginClassApplicator);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model node '" + node.getPath().toString() + "' is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return view;
        }
    }

    private <T> ModelView<? extends T> assertView(ModelNodeData node, ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs) {
        ModelAdapter adapter = node.getAdapter();
        ModelView<? extends T> view = adapter.asWritable(binding.getReference().getType(), node, sourceDescriptor, inputs, modelRuleSourceApplicator, this, pluginClassApplicator);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Cannot project model element " + binding.getPath() + " to writable type '" + binding.getReference().getType() + "' for rule " + sourceDescriptor);
        } else {
            return view;
        }
    }

    private ModelNodeData doCreate(ModelNodeData node, BoundModelCreator boundCreator) {
        ModelCreator creator = boundCreator.getCreator();
        Inputs inputs = toInputs(boundCreator.getInputs(), boundCreator.getCreator().getDescriptor());

        LOGGER.debug("Creating {} using {}", node.getPath(), creator.getDescriptor());

        try {
            creator.create(node, inputs);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getDescriptor(), e);
        }

        return node;
    }

    private <T> void fireMutation(ModelNodeData node, BoundModelMutator<T> boundMutator) {
        Inputs inputs = toInputs(boundMutator.getInputs(), boundMutator.getMutator().getDescriptor());
        ModelAction<T> mutator = boundMutator.getMutator();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        LOGGER.debug("Mutating {} using {}", node.getPath(), mutator.getDescriptor());

        ModelView<? extends T> view = assertView(node, boundMutator.getSubject(), descriptor, inputs);
        try {
            mutator.execute(node, view.getInstance(), inputs, modelRuleSourceApplicator, this, pluginClassApplicator);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(descriptor, e);
        } finally {
            view.close();
        }
    }

    private Inputs toInputs(Iterable<? extends ModelBinding<?>> bindings, ModelRuleDescriptor ruleDescriptor) {
        ImmutableList.Builder<ModelRuleInput<?>> builder = ImmutableList.builder();
        for (ModelBinding<?> binding : bindings) {
            ModelRuleInput<?> input = toInput(binding, ruleDescriptor);
            builder.add(input);
        }
        return new DefaultInputs(builder.build());
    }

    private <T> ModelRuleInput<T> toInput(ModelBinding<T> binding, ModelRuleDescriptor ruleDescriptor) {
        ModelPath path = binding.getPath();
        ModelNodeData element = require(path);
        ModelView<? extends T> view = assertView(element, binding.getReference().getType(), ruleDescriptor, "toInputs");
        return ModelRuleInput.of(binding, view);
    }

    private class NodeWrapper extends ModelNodeData {
        public NodeWrapper(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
            super(creationPath, descriptor, promise, adapter);
        }

        @Nullable
        @Override
        public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
            return getAdapter().asReadOnly(type, this, ruleDescriptor, modelRuleSourceApplicator, DefaultModelRegistry.this, pluginClassApplicator);
        }

        @Nullable
        @Override
        public <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, @Nullable Inputs inputs) {
            return getAdapter().asWritable(type, this, ruleDescriptor, inputs, modelRuleSourceApplicator, DefaultModelRegistry.this, pluginClassApplicator);
        }

        @Override
        public int getLinkCount(ModelType<?> type) {
            int count = 0;
            for (ModelNodeData linked : getLinks().values()) {
                if (linked.getPromise().canBeViewedAsWritable(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Set<String> getLinkNames(ModelType<?> type) {
            Set<String> names = new TreeSet<String>();
            for (Map.Entry<String, ModelNodeData> entry : getLinks().entrySet()) {
                if (entry.getValue().getPromise().canBeViewedAsWritable(type)) {
                    names.add(entry.getKey());
                }
            }
            return names;
        }

        @Override
        public Set<MutableModelNode> getLinks(ModelType<?> type) {
            Set<MutableModelNode> nodes = new LinkedHashSet<MutableModelNode>();
            for (ModelNodeData linked : getLinks().values()) {
                if (linked.getPromise().canBeViewedAsWritable(type)) {
                    nodes.add(linked);
                }
            }
            return nodes;
        }

        @Override
        public boolean hasLink(String name, ModelType<?> type) {
            ModelNodeData linked = getLink(name);
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
        public <T> void applyToAllLinks(final ModelActionRole type, final ModelAction<T> action) {
            if (action.getSubject().getPath() != null) {
                throw new IllegalArgumentException("Linked element action reference must have null path.");
            }

            registerListener(new ModelCreationListener() {
                @Nullable
                @Override
                public ModelPath matchParent() {
                    return getPath();
                }

                @Nullable
                @Override
                public ModelType<?> matchType() {
                    return action.getSubject().getType();
                }

                @Override
                public boolean onCreate(ModelNodeData node) {
                    bind(ModelReference.of(node.getPath(), action.getSubject().getType()), type, action, ModelPath.ROOT);
                    return false;
                }
            });
        }

        @Override
        public void addReference(ModelCreator creator) {
            addLink(creator);
        }

        @Override
        public void addLink(ModelCreator creator) {
            if (!getPath().isDirectChild(creator.getPath())) {
                throw new IllegalArgumentException(String.format("Linked element creator has a path (%s) which is not a child of this node (%s).", creator.getPath(), getPath()));
            }
            doCreate(this, creator);
        }

        @Override
        public void removeLink(String name) {
            remove(getPath().child(name));
        }

        @Override
        public void ensureUsable() {
            transition(this, DefaultsApplied, true);
        }

        @Override
        public <T> void setPrivateData(ModelType<T> type, T object) {
            if (!isMutable()) {
                throw new IllegalStateException(String.format("Cannot set value for model element '%s' as this element is not mutable.", getPath()));
            }
            super.setPrivateData(type, object);
        }
    }

    private static class MutationKey {
        final ModelPath path;
        final ModelActionRole type;

        public MutationKey(ModelPath path, ModelActionRole type) {
            this.path = path;
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            MutationKey other = (MutationKey) obj;
            return path.equals(other.path) && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            return path.hashCode() ^ type.hashCode();
        }
    }

    private static abstract class BinderCreationListener extends ModelCreationListener {
        final ModelRuleDescriptor descriptor;
        final ModelReference<?> reference;
        final boolean writable;

        public BinderCreationListener(ModelRuleDescriptor descriptor, ModelReference<?> reference, boolean writable) {
            this.descriptor = descriptor;
            this.reference = reference;
            this.writable = writable;
        }

        boolean isTypeCompatible(ModelPromise promise) {
            return writable ? promise.canBeViewedAsWritable(reference.getType()) : promise.canBeViewedAsReadOnly(reference.getType());
        }
    }

    private static class PathBinderCreationListener extends BinderCreationListener {
        private final Action<? super ModelPath> bindAction;
        private final ModelPath path;

        public PathBinderCreationListener(ModelRuleDescriptor descriptor, ModelReference<?> reference, ModelPath scope, boolean writable, Action<? super ModelPath> bindAction) {
            super(descriptor, reference, writable);
            this.bindAction = bindAction;
            this.path = scope.scope(reference.getPath());
        }

        @Nullable
        @Override
        public ModelPath matchPath() {
            return path;
        }

        public boolean onCreate(ModelNodeData node) {
            ModelRuleDescriptor creatorDescriptor = node.getDescriptor();
            ModelPath path = node.getPath();
            ModelPromise promise = node.getPromise();
            if (isTypeCompatible(promise)) {
                bindAction.execute(path);
                return true; // bound by type and path, stop listening
            } else {
                throw new InvalidModelRuleException(descriptor, new ModelRuleBindingException(
                        IncompatibleTypeReferenceReporter.of(creatorDescriptor, promise, reference, writable).asString()
                ));
            }
        }
    }

    private static class OneOfTypeBinderCreationListener extends BinderCreationListener {
        private final Action<? super ModelPath> bindAction;
        private ModelPath boundTo;
        private ModelRuleDescriptor boundToCreator;
        private final ModelPath scope;

        public OneOfTypeBinderCreationListener(ModelRuleDescriptor descriptor, ModelReference<?> reference, ModelPath scope, boolean writable, Action<? super ModelPath> bindAction) {
            super(descriptor, reference, writable);
            this.bindAction = bindAction;
            this.scope = scope;
        }

        @Nullable
        @Override
        public ModelPath matchParent() {
            return null;
        }

        @Override
        public ModelPath matchScope() {
            return scope;
        }

        @Nullable
        @Override
        public ModelType<?> matchType() {
            return reference.getType();
        }

        public boolean onCreate(ModelNodeData node) {
            ModelRuleDescriptor creatorDescriptor = node.getDescriptor();
            ModelPath path = node.getPath();
            if (boundTo != null) {
                throw new InvalidModelRuleException(descriptor, new ModelRuleBindingException(
                        new AmbiguousBindingReporter(reference, boundTo, boundToCreator, path, creatorDescriptor).asString()
                ));
            } else {
                bindAction.execute(path);
                boundTo = path;
                boundToCreator = creatorDescriptor;
                return false; // don't unregister listener, need to keep listening for other potential bindings
            }
        }
    }
}
