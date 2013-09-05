package org.gradle.model.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.model.ModelPath;

class ModelMutation<T> {

    private final ModelMutator<T> mutator;
    private final ImmutableList<ModelPath> inputPaths;

    public ModelMutation(ModelMutator<T> mutator, ImmutableList<ModelPath> inputPaths) {
        this.mutator = mutator;
        this.inputPaths = inputPaths;
    }

    public ModelMutator<T> getMutator() {
        return mutator;
    }

    public ImmutableList<ModelPath> getInputPaths() {
        return inputPaths;
    }
}
