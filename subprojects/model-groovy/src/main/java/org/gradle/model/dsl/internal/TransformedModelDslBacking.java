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
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.BiAction;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking;
import org.gradle.model.dsl.internal.transform.InputReferences;
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

    private static final Transformer<InputReferences, Closure<?>> INPUT_PATHS_EXTRACTOR = new Transformer<InputReferences, Closure<?>>() {
        public InputReferences transform(Closure<?> closure) {
            InputReferences inputs = new InputReferences();
            RuleMetadata ruleMetadata = getRuleMetadata(closure);
            inputs.absolutePaths(ruleMetadata.absoluteInputPaths(), ruleMetadata.absoluteInputLineNumbers());
            inputs.relativePaths(ruleMetadata.relativeInputPaths(), ruleMetadata.relativeInputLineNumbers());
            return inputs;
        }
    };

    private final ModelRegistry modelRegistry;
    private final Transformer<? extends InputReferences, ? super Closure<?>> inputPathsExtractor;
    private final Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor;
    private final ModelSchemaStore schemaStore;

    public TransformedModelDslBacking(ModelRegistry modelRegistry, ModelSchemaStore schemaStore, RelativeFilePathResolver relativeFilePathResolver) {
        this(modelRegistry, schemaStore, INPUT_PATHS_EXTRACTOR, new RelativePathSourceLocationTransformer(relativeFilePathResolver));
    }

    TransformedModelDslBacking(ModelRegistry modelRegistry, ModelSchemaStore schemaStore, Transformer<? extends InputReferences, ? super Closure<?>> inputPathsExtractor, Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor) {
        this.modelRegistry = modelRegistry;
        this.schemaStore = schemaStore;
        this.inputPathsExtractor = inputPathsExtractor;
        this.ruleLocationExtractor = ruleLocationExtractor;
    }

    public void configure(String modelPathString, Closure<?> closure) {
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelRuleDescriptor descriptor = toDescriptor(sourceLocation, modelPath);
        registerAction(modelPath, Object.class, descriptor, ModelActionRole.Mutate, closure);
    }

    public <T> void create(String modelPathString, @DelegatesTo.Target Class<T> type, @DelegatesTo(genericTypeIndex = 0) Closure<?> closure) {
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelSchema<T> schema = schemaStore.getSchema(ModelType.of(type));
        ModelRuleDescriptor descriptor = toDescriptor(sourceLocation, modelPath);
        if (!schema.getKind().isManaged()) {
            throw new InvalidModelRuleDeclarationException(descriptor, "Cannot create an element of type " + type.getName() + " as it is not a managed type");
        }
        modelRegistry.create(ModelCreators.of(modelPath, schema.getNodeInitializer()).descriptor(descriptor).build());
        registerAction(modelPath, type, descriptor, ModelActionRole.Initialize, closure);
    }

    private <T> void registerAction(final ModelPath modelPath, Class<T> viewType, final ModelRuleDescriptor descriptor, final ModelActionRole role, final Closure<?> closure) {
        final ModelReference<T> reference = ModelReference.of(modelPath, viewType);
        ModelAction<T> action = DirectNodeNoInputsModelAction.of(reference, descriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode modelNode) {
                InputReferences inputs = inputPathsExtractor.transform(closure);
                List<String> absolutePaths = inputs.getAbsolutePaths();
                List<Integer> absolutePathLineNumbers = inputs.getAbsolutePathLineNumbers();
                List<String> relativePaths = inputs.getRelativePaths();
                List<Integer> relativePathLineNumbers = inputs.getRelativePathLineNumbers();
                List<ModelReference<?>> references = Lists.newArrayListWithCapacity(absolutePaths.size() + inputs.getRelativePaths().size());
                for (int i = 0; i < absolutePaths.size(); i++) {
                    String description = String.format("@ line %d", absolutePathLineNumbers.get(i));
                    references.add(ModelReference.untyped(ModelPath.path(absolutePaths.get(i)), description));
                }
                for (int i = 0; i < relativePaths.size(); i++) {
                    String description = String.format("@ line %d", relativePathLineNumbers.get(i));
                    references.add(ModelReference.untyped(ModelPath.path(relativePaths.get(i)), description));
                }

                ModelAction<T> runClosureAction = InputUsingModelAction.of(reference, descriptor, references, new ExecuteClosure<T>(closure));
                modelRegistry.configure(role, runClosureAction);
            }
        });
        modelRegistry.configure(ModelActionRole.DefineRules, action);
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

    private static class RelativePathSourceLocationTransformer implements Transformer<SourceLocation, Closure<?>> {
        private final RelativeFilePathResolver relativeFilePathResolver;

        public RelativePathSourceLocationTransformer(RelativeFilePathResolver relativeFilePathResolver) {
            this.relativeFilePathResolver = relativeFilePathResolver;
        }

        @Override
        public SourceLocation transform(Closure<?> closure) {
            RuleMetadata ruleMetadata = getRuleMetadata(closure);
            String relativePath = relativeFilePathResolver.resolveAsRelativePath(ruleMetadata.absoluteScriptSourceLocation());
            return new SourceLocation(relativePath, relativePath, ruleMetadata.lineNumber(), ruleMetadata.columnNumber());
        }
    }
}
