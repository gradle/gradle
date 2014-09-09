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

package org.gradle.model.dsl.internal;

import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.model.dsl.ModelDsl;
import org.gradle.model.dsl.internal.transform.ExtractedInputs;
import org.gradle.model.dsl.internal.transform.RulesBlock;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

public class DefaultModelDsl implements ModelDsl {

    private static final InputReferencesExtractor INPUT_PATHS_EXTRACTOR = new InputReferencesExtractor();

    private final ModelRegistry modelRegistry;
    private final Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor;

    public DefaultModelDsl(ModelRegistry modelRegistry) {
        this(modelRegistry, INPUT_PATHS_EXTRACTOR);
    }

    DefaultModelDsl(ModelRegistry modelRegistry, Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor) {
        this.modelRegistry = modelRegistry;
        this.inputPathsExtractor = inputPathsExtractor;
    }

    public void configure(String modelPathString, Closure<?> configuration) {
        List<ModelReference<?>> references = inputPathsExtractor.transform(configuration);
        ModelPath modelPath = ModelPath.path(modelPathString);
        modelRegistry.mutate(new ClosureBackedModelMutator(configuration, references, modelPath));
    }

    private static class InputReferencesExtractor implements Transformer<List<ModelReference<?>>, Closure<?>> {

        private static final StringPathToUntypeReference STRING_PATH_TO_UNTYPE_REFERENCE = new StringPathToUntypeReference();

        public List<ModelReference<?>> transform(Closure<?> closure) {
            ExtractedInputs extractedInputs = closure.getClass().getAnnotation(ExtractedInputs.class);
            if (extractedInputs == null) {
                return Collections.emptyList();
            } else {
                return CollectionUtils.collect(extractedInputs.value(), STRING_PATH_TO_UNTYPE_REFERENCE);
            }
        }

        private static class StringPathToUntypeReference implements Transformer<ModelReference<?>, String> {
            public ModelReference<?> transform(String s) {
                return ModelReference.untyped(ModelPath.path(s));
            }
        }
    }

    public static boolean isRulesBlock(Closure<?> closure) {
        Class<? extends Closure> closureClass = closure.getClass();
        RulesBlock annotation = closureClass.getAnnotation(RulesBlock.class);
        return annotation != null;
    }
}
