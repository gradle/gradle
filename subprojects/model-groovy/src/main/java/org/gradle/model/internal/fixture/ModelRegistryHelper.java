/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.fixture;

import org.gradle.api.Action;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;

import java.util.Collections;
import java.util.List;

import static org.gradle.model.internal.core.ModelPath.nonNullValidatedPath;

/**
 * A helper for adding rules to a model registry.
 *
 * Allows unsafe use of the model registry by allow registering of rules that can close over external, unmanaged, state.
 */
public class ModelRegistryHelper {

    // TODO this is in main because it is used by some integ test and therefore needs to be in the distribution
    //      once that's fixed this should go to testFixtures

    private final ModelRegistry modelRegistry;

    public ModelRegistryHelper(ModelRegistryScope modelRegistryScope) {
        this(modelRegistryScope.getModelRegistry());
    }

    public ModelRegistryHelper(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public ModelRegistryHelper configure(String modelPath, Action<? super Object> configurer) {
        return configure(modelPath, ModelType.UNTYPED, configurer);
    }

    public <T> ModelRegistryHelper configure(ModelType<T> type, Action<? super T> configurer) {
        return configure(null, type, configurer);
    }

    public <T> ModelRegistryHelper configure(Class<T> type, Action<? super T> configurer) {
        return configure(ModelType.of(type), configurer);
    }

    public <T> ModelRegistryHelper configure(final String modelPathString, final ModelType<T> type, final Action<? super T> configurer) {
        final ModelPath modelPath = ModelPath.validatedPath(modelPathString);

        modelRegistry.mutate(new ModelMutator<T>() {
            public ModelReference<T> getSubject() {
                return ModelReference.of(modelPath, type);
            }

            public void mutate(T object, Inputs inputs) {
                configurer.execute(object);
            }

            public List<ModelReference<?>> getInputs() {
                return Collections.emptyList();
            }

            public ModelRuleDescriptor getDescriptor() {
                return new SimpleModelRuleDescriptor("ModelRegistryHelper.configure - " + modelPathString);
            }
        });

        return this;
    }

    public <T> T get(String path, Class<T> type) {
        return modelRegistry.get(nonNullValidatedPath(path), ModelType.of(type));
    }

}
