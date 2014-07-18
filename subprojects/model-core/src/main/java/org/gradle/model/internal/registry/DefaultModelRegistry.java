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
import com.google.common.collect.*;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;

import java.util.*;

public class DefaultModelRegistry implements ModelRegistry {

    /*
        Things we aren't doing and should:

        - Detecting cycles between config rules
        - Detecting dangling, unbound, rules
        - Detecting model elements with no object at their parent path
        - Detecting mutation rules registering parent model

     */
    private final Map<ModelPath, ModelElement> store = new HashMap<ModelPath, ModelElement>();

    private final Map<ModelPath, ModelCreator> creations = new HashMap<ModelPath, ModelCreator>();
    private final Multimap<ModelPath, ModelMutator<?>> mutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, List<ModelPath>> usedMutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, ModelMutator<?>> finalizers = ArrayListMultimap.create();
    private final Multimap<ModelPath, List<ModelPath>> usedFinalizers = ArrayListMultimap.create();

    private final List<ModelCreationListener> modelCreationListeners = new LinkedList<ModelCreationListener>();

    public void create(ModelCreator creator) {
        ModelPath path = creator.getPath();
        ModelCreator existingCreation = creations.get(path);

        if (existingCreation != null) {
            throw new DuplicateModelException(
                    String.format(
                            "Cannot register model creation rule '%s' for path '%s' as the rule '%s' is already registered to create a model element at this path",
                            toString(creator.getSourceDescriptor()),
                            path,
                            toString(existingCreation.getSourceDescriptor())
                    )
            );
        }

        ModelElement existing = store.get(path);
        if (existing != null) {
            throw new DuplicateModelException(
                    String.format(
                            "Cannot register model creation rule '%s' for path '%s' as the rule '%s' is already registered (and the model element has been created)",
                            toString(creator.getSourceDescriptor()),
                            path,
                            toString(existing.getCreatorDescriptor())
                    )
            );
        }

        notifyCreationListeners(creator);
        creations.put(path, creator);
    }

    private static String toString(ModelRuleSourceDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    public <T> void mutate(ModelMutator<T> mutator) {
        ModelPath path = mutator.getReference().getPath();
        assertNotFinalized(path);
        mutators.put(path, mutator);
    }

    public <T> void finalize(ModelMutator<T> mutator) {
        ModelPath path = mutator.getReference().getPath();
        assertNotFinalized(path);
        finalizers.put(path, mutator);
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

        ModelCreator creator = creations.get(path);
        if (creator != null) {
            return toState(creator.getPath(), ModelState.Status.PENDING);
        }

        return null;
    }

    private ModelState toState(ModelPath path, ModelState.Status status) {
        return new ModelState(path, status);
    }

    public void registerListener(ModelCreationListener listener) {
        boolean remove;

        for (Map.Entry<ModelPath, ModelCreator> entry : creations.entrySet()) {
            ModelCreator creator = entry.getValue();
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

    private boolean isDependedOn(ModelPath candidate) {
        Transformer<Iterable<ModelPath>, ModelMutator<?>> extractInputPaths = new Transformer<Iterable<ModelPath>, ModelMutator<?>>() {
            public Iterable<ModelPath> transform(ModelMutator<?> original) {
                return Iterables.transform(original.getInputBindings(), new Function<ModelReference<?>, ModelPath>() {
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

        ModelCreator creation = removeCreator(path);
        return createAndClose(creation);
    }

    private ModelElement createAndClose(ModelCreator creation) {
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

    private void fireMutations(ModelElement element, Iterable<ModelMutator<?>> mutators, Multimap<ModelPath, List<ModelPath>> used) {
        for (ModelMutator<?> mutator : mutators) {
            fireMutation(element, mutator);
            List<ModelPath> inputPaths = Lists.transform(mutator.getInputBindings(), new Function<ModelReference<?>, ModelPath>() {
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

    private <T> ModelView<? extends T> assertView(ModelElement element, ModelReference<T> reference, ModelRuleSourceDescriptor sourceDescriptor, Inputs inputs) {
        ModelAdapter adapter = element.getAdapter();
        ModelView<? extends T> view = adapter.asWritable(reference, sourceDescriptor, inputs, this);
        if (view == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Cannot project model element " + reference.getPath() + " to writable type '" + reference.getType() + "' for rule " + sourceDescriptor);
        } else {
            return view;
        }
    }

    private ModelCreator removeCreator(ModelPath path) {
        ModelCreator creator = creations.remove(path);
        if (creator == null) {
            throw new IllegalStateException("No creator for '" + path + "'");
        } else {
            return creator;
        }
    }

    private ModelElement doCreate(ModelCreator creator) {
        ModelPath path = creator.getPath();
        Inputs inputs = toInputs(creator.getInputBindings());

        ModelAdapter adapter;
        try {
            adapter = creator.create(inputs);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getSourceDescriptor(), e);
        }

        ModelElement element = toElement(adapter, creator);
        store.put(path, element);
        return element;
    }

    private ModelElement toElement(ModelAdapter adapter, ModelCreator creator) {
        return new ModelElement(creator.getPath(), adapter, creator.getSourceDescriptor());
    }

    private <T> void fireMutation(ModelElement element, ModelMutator<T> mutator) {
        Inputs inputs = toInputs(mutator.getInputBindings());
        ModelView<? extends T> view = assertView(element, mutator.getReference(), mutator.getSourceDescriptor(), inputs);
        try {
            mutator.mutate(view.getInstance(), inputs);
        } catch (Exception e) {
            throw new ModelRuleExecutionException(mutator.getSourceDescriptor(), e);
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

}
