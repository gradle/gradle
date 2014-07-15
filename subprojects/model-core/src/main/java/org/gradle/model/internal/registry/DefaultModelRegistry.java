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


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;

import java.util.*;

import static org.gradle.util.CollectionUtils.collect;

public class DefaultModelRegistry implements ModelRegistry {

    private final Map<ModelPath, ModelElement<?>> store = new HashMap<ModelPath, ModelElement<?>>();

    private final Map<ModelPath, ModelCreation<?>> creations = new HashMap<ModelPath, ModelCreation<?>>();
    private final Multimap<ModelPath, ModelMutation<?>> mutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, ImmutableList<ModelPath>> usedMutators = ArrayListMultimap.create();
    private final Multimap<ModelPath, ModelMutation<?>> finalizers = ArrayListMultimap.create();
    private final Multimap<ModelPath, ImmutableList<ModelPath>> usedFinalizers = ArrayListMultimap.create();

    private final List<ModelCreationListener> modelCreationListeners = new LinkedList<ModelCreationListener>();

    public <T> void create(List<String> inputPaths, ModelCreator<T> creator) {
        ModelPath path = creator.getReference().getPath();
        ModelCreation<?> existingCreation = creations.get(path);

        if (existingCreation != null) {
            throw new DuplicateModelException(
                    String.format(
                            "Cannot register model creation rule '%s' for path '%s' as the rule '%s' is already registered to create a model element at this path",
                            toString(creator.getSourceDescriptor()),
                            path,
                            toString(existingCreation.getCreator().getSourceDescriptor())
                    )
            );
        }

        ModelElement<?> existing = store.get(path);
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
        creations.put(path, new ModelCreation<T>(creator, toModelPaths(inputPaths)));
    }

    private static String toString(ModelRuleSourceDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        descriptor.describeTo(stringBuilder);
        return stringBuilder.toString();
    }

    private static ImmutableList<ModelPath> toModelPaths(List<String> inputPaths) {
        return ImmutableList.copyOf(collect(inputPaths, new ToModelPath()));
    }

    public <T> void mutate(List<String> inputPaths, ModelMutator<T> mutator) {
        mutate(new ModelMutation<T>(mutator, toModelPaths(inputPaths)));
    }

    public <T> void mutate(ModelMutation<T> mutation) {
        ModelPath path = mutation.getMutator().getReference().getPath();
        assertNotFinalized(path);
        mutators.put(path, mutation);
    }

    public <T> void finalize(List<String> inputPaths, ModelMutator<T> mutator) {
        finalize(new ModelMutation<T>(mutator, toModelPaths(inputPaths)));
    }

    public <T> void finalize(ModelMutation<T> mutation) {
        ModelPath path = mutation.getMutator().getReference().getPath();
        assertNotFinalized(path);
        finalizers.put(path, mutation);
    }

    private void assertNotFinalized(ModelPath path) {
        if (store.containsKey(path)) {
            throw new IllegalStateException("model '" + path + "' is finalized");
        }
    }

    public <T> T get(ModelReference<T> reference) {
        ModelElement<?> model = get(reference.getPath());
        ModelElement<? extends T> typed = assertType(model, reference.getType(), "get(ModelReference)");
        return typed.getInstance();
    }

    public <T> ModelElement<? extends T> element(ModelReference<T> reference) {
        ModelElement<?> model = get(reference.getPath());
        return assertType(model, reference.getType(), "element(ModelReference)");
    }

    public ModelState<?> state(ModelPath path) {
        ModelElement<?> closed = store.get(path);
        if (closed != null) {
            return toState(closed.getReference(), ModelState.Status.FINALIZED);
        }

        ModelCreation<?> creation = creations.get(path);
        if (creation != null) {
            return toState(creation.getCreator().getReference(), ModelState.Status.PENDING);
        }

        return null;
    }

    private <T> ModelState<T> toState(ModelReference<T> reference, ModelState.Status status) {
        return new ModelState<T>(reference, status);
    }

    public void registerListener(ModelCreationListener listener) {
        boolean remove;

        for (Map.Entry<ModelPath, ModelCreation<?>> entry : creations.entrySet()) {
            remove = listener.onCreate(entry.getValue().getCreator().getReference());
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
        Transformer<Iterable<ModelPath>, ModelMutation<?>> extractInputPaths = new Transformer<Iterable<ModelPath>, ModelMutation<?>>() {
            public Iterable<ModelPath> transform(ModelMutation<?> original) {
                return original.getInputPaths();
            }
        };

        Transformer<ImmutableList<ModelPath>, ImmutableList<ModelPath>> passThrough = Transformers.noOpTransformer();

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

    private ModelElement<?> get(ModelPath path) {
        if (store.containsKey(path)) {
            return store.get(path);
        }

        ModelCreation<?> creation = removeCreation(path);
        return createAndClose(creation);
    }

    private <T> ModelElement<T> createAndClose(ModelCreation<T> creation) {
        ModelElement<T> element = create(creation);
        close(element);
        return element;
    }

    private <T> void close(ModelElement<T> model) {
        ModelPath path = model.getReference().getPath();
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

    private <T> void fireMutations(ModelElement<T> model, Iterable<ModelMutation<?>> mutations, Multimap<ModelPath, ImmutableList<ModelPath>> used) {
        for (ModelMutation<?> mutation : mutations) {
            ModelType<?> mutationTargetType = mutation.getMutator().getReference().getType();

            if (mutationTargetType.isAssignableFrom(model.getReference().getType())) {
                @SuppressWarnings("unchecked") ModelMutation<? super T> castMutation = (ModelMutation<? super T>) mutation;
                fireMutation(model, castMutation);
                ImmutableList<ModelPath> inputPaths = mutation.getInputPaths();
                used.put(model.getReference().getPath(), inputPaths);
            } else {
                // TODO better exception
                throw new IllegalArgumentException("Cannot fire mutation rule " + mutation + " for " + model + " due to incompatible types");
            }
        }
    }

    @Nullable // if is not type compatible
    private <T> ModelElement<? extends T> type(ModelElement<?> element, ModelType<T> targetType) {
        ModelType<?> elementType = element.getReference().getType();

        if (targetType.isAssignableFrom(elementType)) {
            @SuppressWarnings("unchecked") ModelElement<? extends T> cast = (ModelElement<? extends T>) element;
            return cast;
        } else {
            return null;
        }
    }

    private <T> ModelElement<? extends T> assertType(ModelElement<?> element, ModelType<T> targetType, String msg, Object... msgArgs) {
        ModelElement<? extends T> typed = type(element, targetType);
        if (typed == null) {
            // TODO better error reporting here
            throw new IllegalArgumentException("Model element " + element + " is not compatible with requested " + targetType + " (operation: " + String.format(msg, msgArgs) + ")");
        } else {
            return typed;
        }
    }

    private ModelCreation<?> removeCreation(ModelPath path) {
        ModelCreation<?> creation = creations.remove(path);
        if (creation == null) {
            throw new IllegalStateException("No creator for '" + path + "'");
        } else {
            return creation;
        }
    }

    private <T> ModelElement<T> create(ModelCreation<T> creation) {
        ModelCreator<T> creator = creation.getCreator();
        ModelPath path = creator.getReference().getPath();
        Inputs inputs = toInputs(creation.getInputPaths());

        T created;
        try {
            created = creator.create(inputs);
        } catch (Exception e) {
            // TODO some representation of state of the inputs
            throw new ModelRuleExecutionException(creator.getSourceDescriptor(), e);
        }

        if (created == null) {
            throw new ModelRuleExecutionException(creator.getSourceDescriptor(), "rule returned null");
        }

        ModelElement<T> element = toElement(created, creator);
        store.put(path, element);
        return element;
    }

    private <T> ModelElement<T> toElement(T model, ModelCreator<T> creator) {
        return new ModelElement<T>(creator.getReference(), model, creator.getSourceDescriptor());
    }

    private <T> void fireMutation(ModelElement<T> model, ModelMutation<? super T> modelMutation) {
        ModelMutator<? super T> mutator = modelMutation.getMutator();
        Inputs inputs = toInputs(modelMutation.getInputPaths());
        try {
            mutator.mutate(model.getInstance(), inputs);
        } catch (Exception e) {
            throw new ModelRuleExecutionException(mutator.getSourceDescriptor(), e);
        }
    }

    private Inputs toInputs(Iterable<ModelPath> inputPaths) {
        ImmutableList.Builder<ModelElement<?>> builder = ImmutableList.builder();
        for (ModelPath inputPath : inputPaths) {
            builder.add(get(inputPath));
        }
        return new DefaultInputs(builder.build());
    }

    private <T> void notifyCreationListeners(ModelCreator<T> creator) {
        ListIterator<ModelCreationListener> modelCreationListenerListIterator = modelCreationListeners.listIterator();
        while (modelCreationListenerListIterator.hasNext()) {
            ModelCreationListener next = modelCreationListenerListIterator.next();
            boolean remove = next.onCreate(creator.getReference());
            if (remove) {
                modelCreationListenerListIterator.remove();
            }
        }
    }

    private static class ToModelPath implements Transformer<ModelPath, String> {
        public ModelPath transform(String string) {
            return ModelPath.path(string);
        }
    }


}
