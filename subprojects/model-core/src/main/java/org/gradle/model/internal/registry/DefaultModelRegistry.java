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
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.*;

public class DefaultModelRegistry implements ModelRegistry {

    private static final Function<ModelBinding<?>, ModelReference<?>> BINDING_TO_REFERENCE = new Function<ModelBinding<?>, ModelReference<?>>() {
        @Nullable
        public ModelReference<?> apply(ModelBinding<?> input) {
            return ModelReference.of(input.getPath(), input.getType());
        }
    };
    private static final Predicate<ModelBinding<?>> IS_BOUND = new Predicate<ModelBinding<?>>() {
        public boolean apply(ModelBinding<?> input) {
            return input.isBound();
        }
    };
    /*
                Things we aren't doing and should:

                - Detecting cycles between config rules
                - Detecting dangling, unbound, rules
                - Detecting model elements with no object at their parent path
                - Detecting mutation rules registering parent model
                - Detecting a rule binding the same input twice (maybe that's ok)
                - Detecting a rule trying to bind the same element to mutate and to read

             */
    private final Map<ModelPath, ModelElement> store = new HashMap<ModelPath, ModelElement>();

    private final Map<ModelPath, BoundModelCreator> creations = new HashMap<ModelPath, BoundModelCreator>();
    private final Multimap<ModelPath, BoundModelMutator<?>> mutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, List<ModelPath>> usedMutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, BoundModelMutator<?>> finalizers = ArrayListMultimap.create();
    private final Multimap<ModelPath, List<ModelPath>> usedFinalizers = ArrayListMultimap.create();

    private final List<ModelCreationListener> modelCreationListeners = new LinkedList<ModelCreationListener>();

    private static List<ModelReference<?>> toReferencesUnsafe(List<ModelBinding<?>> inputBindings) {
        // unsafe because we are assuming all fully bound
        return Lists.transform(inputBindings, BINDING_TO_REFERENCE);
    }

    private static boolean allFullyBound(List<ModelBinding<?>> inputBindings) {
        return inputBindings.isEmpty() || Iterables.all(inputBindings, IS_BOUND);
    }

    private static String toString(ModelRuleDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    public void create(ModelCreator creator) {
        ModelPath path = creator.getPath();
        BoundModelCreator existingCreation = creations.get(path);

        if (existingCreation != null) {
            throw new DuplicateModelException(
                    String.format(
                            "Cannot register model creation rule '%s' for path '%s' as the rule '%s' is already registered to create a model element at this path",
                            toString(creator.getDescriptor()),
                            path,
                            toString(existingCreation.getCreator().getDescriptor())
                    )
            );
        }

        ModelElement existing = store.get(path);
        if (existing != null) {
            throw new DuplicateModelException(
                    String.format(
                            "Cannot register model creation rule '%s' for path '%s' as the rule '%s' is already registered (and the model element has been created)",
                            toString(creator.getDescriptor()),
                            path,
                            toString(existing.getCreatorDescriptor())
                    )
            );
        }

        notifyCreationListeners(creator);
        bind(creator);
    }

    public <T> void mutate(ModelMutator<T> mutator) {
        bind(mutator, mutators);
    }

    public <T> void finalize(ModelMutator<T> mutator) {
        bind(mutator, finalizers);
    }

    private void bind(final ModelCreator creator) {
        new CreationBinder(creator, new Action<BoundModelCreator>() {
            public void execute(BoundModelCreator boundModelCreator) {
                add(boundModelCreator);
            }
        });
    }

    private void add(BoundModelCreator boundCreator) {
        creations.put(boundCreator.getCreator().getPath(), boundCreator);
    }

    private <T> void bind(ModelMutator<T> mutator, final Multimap<ModelPath, BoundModelMutator<?>> mutators) {
        new MutationBinder<T>(mutator, new Action<BoundModelMutator<T>>() {
            public void execute(BoundModelMutator<T> boundMutator) {
                add(mutators, boundMutator);
            }
        });
    }

    private <T> void add(Multimap<ModelPath, BoundModelMutator<?>> mutators, BoundModelMutator<T> boundModelMutator) {
        ModelPath path = boundModelMutator.getReference().getPath();
        assertNotFinalized(path);
        mutators.put(path, boundModelMutator);
    }

    private void assertNotFinalized(ModelPath path) {
        if (store.containsKey(path)) {
            throw new IllegalStateException("model '" + path + "' is finalized");
        }
    }

    public <T> T get(ModelReference<T> reference) {
        ModelElement element = get(reference.getPath());
        ModelView<? extends T> typed = assertView(element, reference.getType(), "get(ModelReference)");
        return typed.getInstance();
    }

    public ModelElement element(ModelPath path) {
        return get(path);
    }

    public ModelState state(ModelPath path) {
        ModelElement closed = store.get(path);
        if (closed != null) {
            return toState(closed.getPath(), ModelState.Status.FINALIZED);
        }

        BoundModelCreator creator = creations.get(path);
        if (creator != null) {
            return toState(creator.getCreator().getPath(), ModelState.Status.PENDING);
        }

        return null;
    }

    private ModelState toState(ModelPath path, ModelState.Status status) {
        return new ModelState(path, status);
    }

    public void registerListener(ModelCreationListener listener) {
        boolean remove;

        for (Map.Entry<ModelPath, BoundModelCreator> entry : creations.entrySet()) {
            BoundModelCreator boundCreator = entry.getValue();
            ModelCreator creator = boundCreator.getCreator();
            remove = listener.onCreate(creator.getPath(), creator.getPromise());
            if (remove) {
                return;
            }
        }

        modelCreationListeners.add(listener);
    }

    public void remove(ModelPath path) {
        if (creations.remove(path) == null) {
            if (store.remove(path) == null) {
                throw new RuntimeException("Tried to remove model " + path + " but it is not registered");
            } else {
                if (isDependedOn(path)) {
                    throw new RuntimeException("Tried to remove model " + path + " but it is depended on by other model elements");
                }
            }
        }
    }

    // TODO - this needs to consider partially bound rules
    private boolean isDependedOn(ModelPath candidate) {
        Transformer<Iterable<ModelPath>, BoundModelMutator<?>> extractInputPaths = new Transformer<Iterable<ModelPath>, BoundModelMutator<?>>() {
            public Iterable<ModelPath> transform(BoundModelMutator<?> original) {
                return Iterables.transform(original.getInputReferences(), new Function<ModelReference<?>, ModelPath>() {
                    @Nullable
                    public ModelPath apply(ModelReference<?> input) {
                        return input.getPath();
                    }
                });
            }
        };

        Transformer<List<ModelPath>, List<ModelPath>> passThrough = Transformers.noOpTransformer();

        return hasModelPath(candidate, mutators.values(), extractInputPaths)
                || hasModelPath(candidate, usedMutators.values(), passThrough)
                || hasModelPath(candidate, finalizers.values(), extractInputPaths)
                || hasModelPath(candidate, usedFinalizers.values(), passThrough);
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

    private Set<ModelPath> getPromisedPaths() {
        return ImmutableSet.<ModelPath>builder().addAll(creations.keySet()).build();
    }

    private ModelElement get(ModelPath path) {
        if (store.containsKey(path)) {
            return store.get(path);
        }

        BoundModelCreator creation = removeCreator(path);
        return createAndClose(creation);
    }

    private ModelElement createAndClose(BoundModelCreator creation) {
        ModelElement element = doCreate(creation);
        close(element);
        return element;
    }

    private void close(ModelElement model) {
        ModelPath path = model.getPath();
        fireMutations(model, mutators.removeAll(path), usedMutators);
        fireMutations(model, finalizers.removeAll(path), usedFinalizers);

        // close all the child objects
        Set<ModelPath> promisedPaths = getPromisedPaths();
        for (ModelPath modelPath : promisedPaths) {
            if (path.isDirectChild(modelPath)) {
                get(modelPath);
            }
        }
    }

    private void fireMutations(ModelElement element, Iterable<BoundModelMutator<?>> mutators, Multimap<ModelPath, List<ModelPath>> used) {
        for (BoundModelMutator<?> mutator : mutators) {
            fireMutation(element, mutator);
            List<ModelPath> inputPaths = Lists.transform(mutator.getInputReferences(), new Function<ModelReference<?>, ModelPath>() {
                @Nullable
                public ModelPath apply(ModelReference<?> input) {
                    return input.getPath();
                }
            });
            used.put(element.getPath(), inputPaths);
        }
    }

    private <T> ModelView<? extends T> assertView(ModelElement element, ModelType<T> targetType, String msg, Object... msgArgs) {
        ModelAdapter adapter = element.getAdapter();
        ModelView<? extends T> view = adapter.asReadOnly(targetType);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model element " + element.getPath() + " is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return view;
        }
    }

    private <T> ModelView<? extends T> assertView(ModelElement element, ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, Inputs inputs) {
        ModelAdapter adapter = element.getAdapter();
        ModelView<? extends T> view = adapter.asWritable(reference, sourceDescriptor, inputs, this);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Cannot project model element " + reference.getPath() + " to writable type '" + reference.getType() + "' for rule " + sourceDescriptor);
        } else {
            return view;
        }
    }

    private BoundModelCreator removeCreator(ModelPath path) {
        BoundModelCreator creator = creations.remove(path);
        if (creator == null) {
            throw new IllegalStateException("No creator for '" + path + "'");
        } else {
            return creator;
        }
    }

    private ModelElement doCreate(BoundModelCreator boundCreator) {
        ModelCreator creator = boundCreator.getCreator();
        ModelPath path = creator.getPath();
        Inputs inputs = toInputs(boundCreator.getInputReferences());

        ModelAdapter adapter;
        try {
            adapter = creator.create(inputs);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getDescriptor(), e);
        }

        ModelElement element = toElement(adapter, creator);
        store.put(path, element);
        return element;
    }

    private ModelElement toElement(ModelAdapter adapter, ModelCreator creator) {
        return new ModelElement(creator.getPath(), adapter, creator.getDescriptor());
    }

    private <T> void fireMutation(ModelElement element, BoundModelMutator<T> boundMutator) {
        Inputs inputs = toInputs(boundMutator.getInputReferences());
        ModelMutator<T> mutator = boundMutator.getMutator();
        ModelRuleDescriptor descriptor = mutator.getDescriptor();

        ModelView<? extends T> view = assertView(element, boundMutator.getReference(), descriptor, inputs);
        try {
            mutator.mutate(view.getInstance(), inputs);
        } catch (Exception e) {
            throw new ModelRuleExecutionException(descriptor, e);
        } finally {
            view.close();
        }
    }

    private Inputs toInputs(Iterable<? extends ModelReference<?>> references) {
        ImmutableList.Builder<ModelRuleInput<?>> builder = ImmutableList.builder();
        for (ModelReference<?> reference : references) {
            ModelRuleInput<?> input = toInput(reference);
            builder.add(input);
        }
        return new DefaultInputs(builder.build());
    }

    private <T> ModelRuleInput<T> toInput(ModelReference<T> reference) {
        ModelPath path = reference.getPath();
        ModelElement element = element(path);
        ModelView<? extends T> view = assertView(element, reference.getType(), "toInputs");
        return ModelRuleInput.of(reference, view);
    }

    private void notifyCreationListeners(ModelCreator creator) {
        ListIterator<ModelCreationListener> modelCreationListenerListIterator = modelCreationListeners.listIterator();
        while (modelCreationListenerListIterator.hasNext()) {
            ModelCreationListener next = modelCreationListenerListIterator.next();
            boolean remove = next.onCreate(creator.getPath(), creator.getPromise());
            if (remove) {
                modelCreationListenerListIterator.remove();
            }
        }
    }

    private static class BoundModelCreator {
        private final ModelCreator creator;
        private final List<ModelReference<?>> inputReferences;

        private BoundModelCreator(ModelCreator creator, List<ModelReference<?>> inputReferences) {
            this.creator = creator;
            this.inputReferences = inputReferences;
        }

        public ModelCreator getCreator() {
            return creator;
        }

        public List<ModelReference<?>> getInputReferences() {
            return inputReferences;
        }
    }

    private static class BoundModelMutator<T> {
        private final ModelMutator<T> mutator;
        private final ModelReference<T> reference;
        private final List<ModelReference<?>> inputReferences;

        private BoundModelMutator(ModelMutator<T> mutator, ModelReference<T> reference, List<ModelReference<?>> inputReferences) {
            this.mutator = mutator;
            this.reference = reference;
            this.inputReferences = inputReferences;
        }

        public ModelMutator<T> getMutator() {
            return mutator;
        }

        public ModelReference<T> getReference() {
            return reference;
        }

        public List<ModelReference<?>> getInputReferences() {
            return inputReferences;
        }
    }

    private class CreationBinder {

        private final ModelCreator creator;
        private final Action<? super BoundModelCreator> onBound;

        private List<ModelBinding<?>> inputBindings;

        public CreationBinder(ModelCreator creator, Action<? super BoundModelCreator> onBound) {
            this.creator = creator;
            this.onBound = onBound;
            this.inputBindings = new ArrayList<ModelBinding<?>>(creator.getInputBindings());

            if (allFullyBound(inputBindings)) {
                fire();
            } else {
                registerListener(new ModelCreationListener() {
                    public boolean onCreate(ModelPath path, ModelPromise promise) {
                        // TODO this needs optimisation
                        boolean unsatisfied = false;

                        ListIterator<ModelBinding<?>> iterator = inputBindings.listIterator();
                        while (iterator.hasNext()) {
                            ModelBinding<?> inputBinding = iterator.next();
                            if (!inputBinding.isBound()) {
                                if (promise.asReadOnly(inputBinding.getType())) {
                                    iterator.set(inputBinding.bind(path));
                                } else {
                                    unsatisfied = true;
                                }
                            }
                        }

                        if (unsatisfied) {
                            return false;
                        } else {
                            fire();
                            return true;
                        }
                    }
                });
            }
        }

        private void fire() {
            List<ModelReference<?>> references = toReferencesUnsafe(inputBindings);
            onBound.execute(new BoundModelCreator(creator, references));
        }
    }

    private class MutationBinder<T> {

        private final ModelMutator<T> mutator;
        private final Action<? super BoundModelMutator<T>> onBound;

        private ModelBinding<T> binding;
        private List<ModelBinding<?>> inputBindings;

        private MutationBinder(ModelMutator<T> mutator, Action<? super BoundModelMutator<T>> onBound) {
            this.mutator = mutator;
            this.onBound = onBound;
            this.inputBindings = new ArrayList<ModelBinding<?>>(mutator.getInputBindings());
            this.binding = mutator.getBinding();

            if (binding.isBound() && allFullyBound(inputBindings)) {
                fire();
            } else {
                registerListener(new ModelCreationListener() {
                    public boolean onCreate(ModelPath path, ModelPromise promise) {
                        boolean unsatisfied = false;
                        if (!binding.isBound()) {
                            if (promise.asWritable(binding.getType())) {
                                binding = binding.bind(path);
                            } else {
                                unsatisfied = true;
                            }
                        }

                        ListIterator<ModelBinding<?>> iterator = inputBindings.listIterator();
                        while (iterator.hasNext()) {
                            ModelBinding<?> inputBinding = iterator.next();
                            if (!inputBinding.isBound()) {
                                if (promise.asReadOnly(inputBinding.getType())) {
                                    iterator.set(inputBinding.bind(path));
                                } else {
                                    unsatisfied = true;
                                }
                            }
                        }

                        if (unsatisfied) {
                            return false;
                        } else {
                            fire();
                            return true;
                        }
                    }
                });
            }
        }

        private void fire() {
            List<ModelReference<?>> references = toReferencesUnsafe(inputBindings);
            onBound.execute(new BoundModelMutator<T>(mutator, ModelReference.of(binding.getPath(), binding.getType()), references));
        }
    }

    public static interface ModelCreationListener {

        boolean onCreate(ModelPath path, ModelPromise promise);

    }
}
