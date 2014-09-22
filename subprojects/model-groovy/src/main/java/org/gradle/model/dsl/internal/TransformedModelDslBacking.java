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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.model.dsl.internal.transform.RuleMetadata;
import org.gradle.model.dsl.internal.transform.RulesBlock;
import org.gradle.model.dsl.internal.transform.SourceLocation;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.List;

public class TransformedModelDslBacking {

    private static final Transformer<List<ModelReference<?>>, Closure<?>> INPUT_PATHS_EXTRACTOR = new Transformer<List<ModelReference<?>>, Closure<?>>() {
        public List<ModelReference<?>> transform(Closure<?> closure) {
            RuleMetadata ruleMetadata = getRuleMetadata(closure);
            String[] paths = ruleMetadata.inputPaths();
            List<ModelReference<?>> references = Lists.newArrayListWithCapacity(paths.length);
            for (int i = 0; i < paths.length; i++) {
                String description = String.format("@ line %d", ruleMetadata.inputLineNumbers()[i]);
                references.add(ModelReference.untyped(ModelPath.path(paths[i]), description));
            }
            return references;
        }
    };

    private static final Transformer<SourceLocation, Closure<?>> RULE_LOCATION_EXTRACTOR = new Transformer<SourceLocation, Closure<?>>() {
        public SourceLocation transform(Closure<?> closure) {
            RuleMetadata ruleMetadata = getRuleMetadata(closure);
            return new SourceLocation(ruleMetadata.scriptSourceDescription(), ruleMetadata.lineNumber(), ruleMetadata.columnNumber());
        }
    };

    private final ModelRegistry modelRegistry;
    private final Object thisObject;
    private final Object owner;
    private final Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor;
    private final Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor;

    public TransformedModelDslBacking(ModelRegistry modelRegistry, Object thisObject, Object owner) {
        this(modelRegistry, thisObject, owner, INPUT_PATHS_EXTRACTOR, RULE_LOCATION_EXTRACTOR);
    }

    TransformedModelDslBacking(ModelRegistry modelRegistry, Object thisObject, Object owner, Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor,
                               Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor) {
        this.modelRegistry = modelRegistry;
        this.thisObject = thisObject;
        this.owner = owner;
        this.inputPathsExtractor = inputPathsExtractor;
        this.ruleLocationExtractor = ruleLocationExtractor;
    }

    public void configure(String modelPathString, Closure<?> configuration) {
        List<ModelReference<?>> references = inputPathsExtractor.transform(configuration);
        SourceLocation sourceLocation = ruleLocationExtractor.transform(configuration);
        ModelPath modelPath = ModelPath.path(modelPathString);
        Closure<?> reownered = configuration.rehydrate(null, owner, thisObject);
        modelRegistry.mutate(new ClosureBackedModelMutator(reownered, references, modelPath, sourceLocation));
    }

    private static RuleMetadata getRuleMetadata(Closure<?> closure) {
        RuleMetadata ruleMetadata = closure.getClass().getAnnotation(RuleMetadata.class);
        if (ruleMetadata == null) {
            throw new IllegalStateException(String.format("Expected %s annotation to be used on the argument closure.", RuleMetadata.class.getName()));
        }
        return ruleMetadata;
    }

    public static boolean isTransformedBlock(Closure<?> closure) {
        Class<? extends Closure> closureClass = closure.getClass();
        RulesBlock annotation = closureClass.getAnnotation(RulesBlock.class);
        return annotation != null;
    }
}
