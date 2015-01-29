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
import com.google.common.base.Predicates;
import com.google.common.collect.*;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
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

    private final ModelGraph modelGraph;
    private final ModelRuleExtractor ruleExtractor;
    private final List<RuleBinder<?>> binders = Lists.newLinkedList();
    private final Map<ModelActionRole, Multimap<ModelPath, RuleBinder<?>>> mutationBindersByActionRole = Maps.newEnumMap(ModelActionRole.class);

    public DefaultModelRegistry(ModelRuleExtractor ruleExtractor) {
        this.ruleExtractor = ruleExtractor;
        for (ModelActionRole role : ModelActionRole.values()) {
            mutationBindersByActionRole.put(role, ArrayListMultimap.<ModelPath, RuleBinder<?>>create());
        }
        modelGraph = new ModelGraph(new ModelElementNode(ModelPath.ROOT, new SimpleModelRuleDescriptor("<root>"), EmptyModelProjection.INSTANCE, EmptyModelProjection.INSTANCE));
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

        registerNode(modelGraph.getRoot(), new ModelElementNode(creator.getPath(), creator.getDescriptor(), creator.getPromise(), creator.getAdapter()), creator);
        return this;
    }

    private ModelNodeInternal registerNode(ModelNodeInternal parent, ModelNodeInternal child, ModelCreator creator) {
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
        bind(creator, node);
        return node;
    }

    @Override
    public <T> DefaultModelRegistry apply(ModelActionRole role, ModelAction<T> action, ModelPath scope) {
        bind(action.getSubject(), role, action, scope);
        return this;
    }

    @Override
    public ModelRegistry apply(Class<? extends RuleSource> rules) {
        modelGraph.getRoot().applyToSelf(rules);
        return this;
    }

    private void bind(ModelCreator creator, ModelNodeInternal node) {
        RuleBinder<Void> binder = createAndRegisterBinder(null, creator.getInputs(), creator.getDescriptor(), ModelPath.ROOT, new RegisterBoundCreator(creator, node));
        node.setCreatorBinder(binder);
        bindInputs(binder, ModelPath.ROOT);
    }

    @SuppressWarnings("unchecked")
    private <T> RuleBinder<T> createAndRegisterBinder(ModelReference<T> subject, List<? extends ModelReference<?>> inputs, ModelRuleDescriptor descriptor, ModelPath scope, final Action<? super RuleBinder<T>> onBind) {
        RuleBinder<T> binder = new RuleBinder<T>(subject, inputs, descriptor, scope, onBind);
        binders.add(binder);
        return binder;
    }

    private <T> void bind(ModelReference<T> subject, ModelActionRole type, ModelAction<T> mutator, ModelPath scope) {
        Multimap<ModelPath, RuleBinder<?>> mutationBinders = mutationBindersByActionRole.get(type);
        RuleBinder<T> binder = createAndRegisterBinder(subject, mutator.getInputs(), mutator.getDescriptor(), scope, new RegisterBoundModelAction<T>(mutator, type, mutationBinders));
        ModelCreationListener listener = listener(binder.getDescriptor(), binder.getSubjectReference(), scope, true, new BindSubject<T>(binder, mutator, type, mutationBinders));
        registerListener(listener);
        bindInputs(binder, scope);
    }

    private void bindInputs(final RuleBinder<?> binder, ModelPath scope) {
        List<? extends ModelReference<?>> inputReferences = binder.getInputReferences();
        for (int i = 0; i < inputReferences.size(); i++) {
            ModelReference<?> input = inputReferences.get(i);
            ModelPath effectiveScope = input.getPath() == null ? ModelPath.ROOT : scope;
            registerListener(listener(binder.getDescriptor(), input, effectiveScope, false, new BindInput(binder, i)));
        }
    }

    private ModelCreationListener listener(ModelRuleDescriptor descriptor, ModelReference<?> reference, ModelPath scope, boolean writable, Action<? super ModelNodeInternal> bindAction) {
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
        if (isDependedOn(path)) {
            throw new RuntimeException("Tried to remove model " + path + " but it is depended on by other model elements");
        }

        modelGraph.remove(path);
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
        if (binders.isEmpty()) {
            return;
        }

        boolean newInputsBound = true;
        while (!binders.isEmpty() && newInputsBound) {
            newInputsBound = false;
            //`binders` is mutated during tryForceBind (removal and insert), can't use foreach
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < binders.size(); i++) {
                newInputsBound = newInputsBound || tryForceBind(binders.get(i));
            }
        }

        if (!binders.isEmpty()) {
            throw unbound(binders);
        }
    }

    private UnboundModelRulesException unbound(Iterable<RuleBinder<?>> binders) {
        ModelPathSuggestionProvider suggestionsProvider = new ModelPathSuggestionProvider(modelGraph.getFlattened().keySet());
        List<? extends UnboundRule> unboundRules = new UnboundRulesProcessor(binders, suggestionsProvider).process();
        return new UnboundModelRulesException(unboundRules);
    }

    // TODO - this needs to consider partially bound rules
    private boolean isDependedOn(ModelPath candidate) {
        // NOTE - we could make this more efficient by storing the dependents of each node on it
        Collection<ModelNodeInternal> allNodes = modelGraph.getFlattened().values();
        Iterable<ModelPath> allDependedUpon = Iterables.concat(Iterables.transform(allNodes, new Function<ModelNodeInternal, Iterable<ModelPath>>() {
            @Override
            public Iterable<ModelPath> apply(ModelNodeInternal input) {
                return input.getDependencies();
            }
        }));

        Predicate<ModelPath> equalTo = Predicates.equalTo(candidate);
        return Iterables.any(allDependedUpon, equalTo);
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

        if (state == Known && desired.ordinal() >= Created.ordinal()) {
            RuleBinder<?> creatorBinder = node.getCreatorBinder();
            if (creatorBinder != null) {
                forceBind(creatorBinder);
            }
            BoundModelCreator creator = node.getCreator();
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
            for (ModelNodeInternal child : node.getLinks()) {
                close(child);
            }
            node.setState(GraphClosed);
        }

        LOGGER.debug("Finished transitioning model element {} from state {} to {}", path, state.name(), desired.name());
    }

    private boolean forceBindReference(ModelReference<?> reference, ModelBinding<?> binding, ModelPath scope) {
        if (binding == null) {
            if (reference.getPath() == null) {
                selfCloseAncestryAndSelf(scope);
            } else {
                selfCloseAncestryAndSelf(reference.getPath().getParent());
            }
            return true;
        }
        return false;
    }

    private boolean tryForceBind(RuleBinder<?> binder) {
        if (maybeFireThenUnregister(binder)) {
            return true;
        }

        boolean boundSomething = false;
        ModelPath scope = binder.getScope();

        if (binder.getSubjectReference() != null && binder.getSubjectBinding() == null) {
            if (forceBindReference(binder.getSubjectReference(), binder.getSubjectBinding(), scope)) {
                boundSomething = binder.getSubjectBinding() != null;
            }
        }

        for (int i = 0; i < binder.getInputReferences().size(); i++) {
            if (forceBindReference(binder.getInputReferences().get(i), binder.getInputBindings().get(i), scope)) {
                boundSomething = boundSomething || binder.getInputBindings().get(i) != null;
            }
        }
        maybeFireThenUnregister(binder);
        return boundSomething;
    }

    private boolean maybeFireThenUnregister(RuleBinder<?> binder) {
        boolean fired = binder.maybeFire();
        if (fired) {
            binders.remove(binder);
        }
        return fired;
    }

    private void forceBind(RuleBinder<?> binder) {
        tryForceBind(binder);
        if (!binder.isBound()) {
            throw unbound(Collections.<RuleBinder<?>>singleton(binder));
        }
    }

    // NOTE: this should only be called from transition() as implicit logic is shared
    private boolean fireMutations(ModelNodeInternal node, ModelPath path, ModelNode.State originalState, ModelActionRole type, ModelNode.State to, ModelNode.State desired) {
        ModelNode.State nodeState = node.getState();
        if (nodeState.ordinal() >= to.ordinal()) {
            return nodeState.ordinal() < desired.ordinal();
        }

        while (true) {
            //MultiMap.get() returns a live collection and force binding rules might change it
            while (!mutationBindersByActionRole.get(type).get(path).isEmpty()) {
                forceBind(mutationBindersByActionRole.get(type).get(path).iterator().next());
            }

            List<BoundModelMutator<?>> mutators = node.removeMutators(type);
            if (mutators.isEmpty()) {
                break;
            }

            for (BoundModelMutator<?> mutator : mutators) {
                fireMutation(mutator);
            }
        }

        node.setState(to);

        if (to == desired) {
            LOGGER.debug("Finished transitioning model element {} from state {} to {}", path, originalState.name(), desired.name());
            return false;
        } else {
            return true;
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

    private ModelNodeInternal doCreate(ModelNodeInternal node, BoundModelCreator boundCreator) {
        ModelCreator creator = boundCreator.getCreator();
        List<ModelView<?>> views = toViews(boundCreator.getInputs(), boundCreator.getCreator().getDescriptor());

        LOGGER.debug("Creating {} using {}", node.getPath(), creator.getDescriptor());

        try {
            creator.create(node, views);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getDescriptor(), e);
        }

        return node;
    }

    private <T> void fireMutation(BoundModelMutator<T> boundMutator) {
        List<ModelView<?>> inputs = toViews(boundMutator.getInputs(), boundMutator.getMutator().getDescriptor());

        ModelNodeInternal node = boundMutator.getSubject();
        ModelAction<T> mutator = boundMutator.getMutator();
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

    private List<ModelView<?>> toViews(List<ModelBinding<?>> bindings, final ModelRuleDescriptor ruleDescriptor) {
        // hot path; create as little as possible…
        @SuppressWarnings("unchecked") ModelView<?>[] array = new ModelView<?>[bindings.size()];
        int i = 0;
        for (ModelBinding<?> binding : bindings) {
            close(binding.getNode());
            ModelPath path = binding.getNode().getPath();
            ModelNodeInternal element = require(path);
            ModelView<?> view = assertView(element, binding.getReference().getType(), ruleDescriptor, "toViews");
            array[i++] = view;
        }
        @SuppressWarnings("unchecked") List<ModelView<?>> views = Arrays.asList(array);
        return views;
    }

    @Override
    public ModelNode node(ModelPath path) {
        return modelGraph.find(path);
    }

    private class ModelReferenceNode extends ModelNodeInternal {
        private ModelNodeInternal target;

        public ModelReferenceNode(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
            super(creationPath, descriptor, promise, adapter);
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
        public <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyToLink(String name, Class<? extends RuleSource> rules) {
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
        public <T> void setPrivateData(ModelType<T> type, T object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureUsable() {
        }
    }

    private class ModelElementNode extends ModelNodeInternal {
        private final Map<String, ModelNodeInternal> links = Maps.newTreeMap();
        private Object privateData;
        private ModelType<?> privateDataType;

        public ModelElementNode(ModelPath creationPath, ModelRuleDescriptor descriptor, ModelPromise promise, ModelAdapter adapter) {
            super(creationPath, descriptor, promise, adapter);
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

        public <T> void setPrivateData(ModelType<T> type, T object) {
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
            Set<String> names = new TreeSet<String>();
            for (Map.Entry<String, ModelNodeInternal> entry : links.entrySet()) {
                if (entry.getValue().getPromise().canBeViewedAsWritable(type)) {
                    names.add(entry.getKey());
                }
            }
            return names;
        }

        @Override
        public Set<MutableModelNode> getLinks(ModelType<?> type) {
            Set<MutableModelNode> nodes = new LinkedHashSet<MutableModelNode>();
            for (ModelNodeInternal linked : links.values()) {
                if (linked.getPromise().canBeViewedAsWritable(type)) {
                    nodes.add(linked);
                }
            }
            return nodes;
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

        public void apply(Class<? extends RuleSource> rules, ModelPath scope) {
            Iterable<ExtractedModelRule> registrations = ruleExtractor.extract(rules);
            for (ExtractedModelRule registration : registrations) {
                // TODO - remove this when we remove the 'rule dependencies' mechanism
                if (!registration.getRuleDependencies().isEmpty()) {
                    throw new IllegalStateException("Rule source " + rules + " cannot have plugin dependencies (introduced by rule " + registration + ")");
                }

                // TODO this is a roundabout path, something like the registrar interface should be implementable by the regsitry and nodes
                registration.applyTo(DefaultModelRegistry.this, scope);
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
                public ModelPath matchParent() {
                    return getPath();
                }

                @Nullable
                @Override
                public ModelType<?> matchType() {
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
            if (!getPath().isDirectChild(creator.getPath())) {
                throw new IllegalArgumentException(String.format("Reference element creator has a path (%s) which is not a child of this node (%s).", creator.getPath(), getPath()));
            }
            registerNode(this, new ModelReferenceNode(creator.getPath(), creator.getDescriptor(), creator.getPromise(), creator.getAdapter()), creator);
        }

        @Override
        public void addLink(ModelCreator creator) {
            if (!getPath().isDirectChild(creator.getPath())) {
                throw new IllegalArgumentException(String.format("Linked element creator has a path (%s) which is not a child of this node (%s).", creator.getPath(), getPath()));
            }
            registerNode(this, new ModelElementNode(creator.getPath(), creator.getDescriptor(), creator.getPromise(), creator.getAdapter()), creator);
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

}
