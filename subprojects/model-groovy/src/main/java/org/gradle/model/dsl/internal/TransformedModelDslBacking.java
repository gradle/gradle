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
import groovy.lang.DelegatesTo;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.BiAction;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking;
import org.gradle.model.dsl.internal.transform.RuleMetadata;
import org.gradle.model.dsl.internal.transform.RulesBlock;
import org.gradle.model.dsl.internal.transform.SourceLocation;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@ThreadSafe
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
    private final Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor;
    private final Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor;
    private final ModelSchemaStore schemaStore;
    private final ModelCreatorFactory modelCreatorFactory;

    public TransformedModelDslBacking(ModelRegistry modelRegistry, ModelSchemaStore schemaStore, ModelCreatorFactory modelCreatorFactory) {
        this(modelRegistry, schemaStore, modelCreatorFactory, INPUT_PATHS_EXTRACTOR, RULE_LOCATION_EXTRACTOR);
    }

    TransformedModelDslBacking(ModelRegistry modelRegistry, ModelSchemaStore schemaStore, ModelCreatorFactory modelCreatorFactory, Transformer<? extends List<ModelReference<?>>, ? super Closure<?>> inputPathsExtractor,
                               Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor) {
        this.modelRegistry = modelRegistry;
        this.schemaStore = schemaStore;
        this.modelCreatorFactory = modelCreatorFactory;
        this.inputPathsExtractor = inputPathsExtractor;
        this.ruleLocationExtractor = ruleLocationExtractor;
    }

    public void configure(String modelPathString, Closure<?> closure) {
        List<ModelReference<?>> inputs = inputPathsExtractor.transform(closure);
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelAction<Object> action = BiActionBackedModelAction.of(ModelReference.of(modelPath), toDescriptor(sourceLocation, modelPath), inputs, new ExecuteClosure<Object>(closure));
        modelRegistry.configure(ModelActionRole.Mutate, action);
    }

    public <T> void create(String modelPathString, @DelegatesTo.Target Class<T> type, @DelegatesTo(genericTypeIndex = 0) Closure<?> closure) {
        List<ModelReference<?>> inputs = inputPathsExtractor.transform(closure);
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelSchema<T> schema = schemaStore.getSchema(ModelType.of(type));
        ModelRuleDescriptor descriptor = toDescriptor(sourceLocation, modelPath);
        if (!schema.getKind().isManaged()) {
            throw new InvalidModelRuleDeclarationException(descriptor, "Cannot create an element of type " + type.getName() + " as it is not a managed type");
        }
        ModelCreator creator = modelCreatorFactory.creator(descriptor, modelPath, schema, inputs, new ExecuteClosure<T>(closure));
        modelRegistry.create(creator);
    }

    public ModelRuleDescriptor toDescriptor(SourceLocation sourceLocation, ModelPath modelPath) {
        return sourceLocation.asDescriptor("model." + modelPath);
    }

    private static RuleMetadata getRuleMetadata(Closure<?> closure) {
        RuleMetadata ruleMetadata = closure.getClass().getAnnotation(RuleMetadata.class);
        if (ruleMetadata == null) {
            throw new IllegalStateException(String.format("Expected %s annotation to be used on the argument closure.", RuleMetadata.class.getName()));
        }
        return ruleMetadata;
    }

    public static boolean isTransformedBlock(Closure<?> closure) {
        Class<?> closureClass = closure.getClass();
        RulesBlock annotation = closureClass.getAnnotation(RulesBlock.class);
        return annotation != null;
    }

    private static class ExecuteClosure<T> implements BiAction<T, List<ModelView<?>>> {
        private final Closure<?> closure;

        public ExecuteClosure(Closure<?> closure) {
            this.closure = closure.rehydrate(null, null, null);
        }

        @Override
        public void execute(final T object, List<ModelView<?>> inputs) {
            RuleInputAccessBacking.runWithContext(inputs, new Runnable() {
                public void run() {
                    new ClosureBackedAction<Object>(closure).execute(object);
                }
            });
        }
    }
}
