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
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.BiAction;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.dsl.internal.inputs.PotentialInput;
import org.gradle.model.dsl.internal.inputs.PotentialInputs;
import org.gradle.model.dsl.internal.transform.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.net.URI;
import java.util.List;
import java.util.Map;

@ThreadSafe
public class TransformedModelDslBacking {
    private final ModelRegistry modelRegistry;
    private final Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor;

    public TransformedModelDslBacking(ModelRegistry modelRegistry, RelativeFilePathResolver relativeFilePathResolver) {
        this(modelRegistry, new RelativePathSourceLocationTransformer(relativeFilePathResolver));
    }

    TransformedModelDslBacking(ModelRegistry modelRegistry, Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor) {
        this.modelRegistry = modelRegistry;
        this.ruleLocationExtractor = ruleLocationExtractor;
    }

    /**
     * Invoked by transformed DSL configuration rules
     */
    public void configure(String modelPathString, Closure<?> closure) {
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelRuleDescriptor descriptor = toDescriptor(sourceLocation, modelPath);
        registerAction(modelPath, Object.class, descriptor, ModelActionRole.Mutate, closure);
    }

    /**
     * Invoked by transformed DSL creation rules
     */
    public <T> void create(String modelPathString, Class<T> type, Closure<?> closure) {
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        ModelPath modelPath = ModelPath.path(modelPathString);
        ModelRuleDescriptor descriptor = toDescriptor(sourceLocation, modelPath);
        try {
            NodeInitializerRegistry nodeInitializerRegistry = modelRegistry.realize(DefaultNodeInitializerRegistry.DEFAULT_REFERENCE.getPath(), DefaultNodeInitializerRegistry.DEFAULT_REFERENCE.getType());
            NodeInitializer nodeInitializer = nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forType(ModelType.of(type)));
            modelRegistry.register(ModelRegistrations.of(modelPath, nodeInitializer).descriptor(descriptor).build());
        } catch (ModelTypeInitializationException e) {
            throw new InvalidModelRuleDeclarationException(descriptor, e);
        }
        registerAction(modelPath, type, descriptor, ModelActionRole.Initialize, closure);
    }

    private <T> void registerAction(final ModelPath modelPath, final Class<T> viewType, final ModelRuleDescriptor descriptor, final ModelActionRole role, final Closure<?> closure) {
        final ModelReference<T> reference = ModelReference.of(modelPath, viewType);
        modelRegistry.configure(ModelActionRole.Initialize, DirectNodeNoInputsModelAction.of(reference, descriptor, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode mutableModelNode) {
                final TransformedClosure transformedClosure = (TransformedClosure) closure;
                InputReferences inputs = transformedClosure.getInputReferences();
                List<InputReference> inputReferences = inputs.getAllReferences();
                final Map<String, PotentialInput> inputValues = Maps.newLinkedHashMap();
                List<ModelReference<?>> inputModelReferences = Lists.newArrayList();

                for (int i = 0; i < inputReferences.size(); i++) {
                    InputReference inputReference = inputReferences.get(i);
                    String description = String.format("@ line %d", inputReference.getLineNumber());
                    String path = inputReference.getPath();
                    if (!inputValues.containsKey(path)) {
                        inputValues.put(path, new PotentialInput(inputModelReferences.size()));
                        inputModelReferences.add(ModelReference.untyped(ModelPath.path(path), description));
                    }
                }

                mutableModelNode.applyToSelf(role, InputUsingModelAction.of(reference, descriptor, inputModelReferences, new BiAction<T, List<ModelView<?>>>() {
                    @Override
                    public void execute(final T t, List<ModelView<?>> modelViews) {
                        transformedClosure.applyRuleInputs(new PotentialInputs(modelViews, inputValues));
                        ClosureBackedAction.execute(t, closure.rehydrate(null, closure.getThisObject(), closure.getThisObject()));
                    }
                }));
            }
        }));
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

    private static class RelativePathSourceLocationTransformer implements Transformer<SourceLocation, Closure<?>> {
        private final RelativeFilePathResolver relativeFilePathResolver;

        public RelativePathSourceLocationTransformer(RelativeFilePathResolver relativeFilePathResolver) {
            this.relativeFilePathResolver = relativeFilePathResolver;
        }

        // TODO given that all the closures are from the same file, we should do the relativising once.
        //      that would entail adding location information to the model {} outer closure.
        @Override
        public SourceLocation transform(Closure<?> closure) {
            RuleMetadata ruleMetadata = getRuleMetadata(closure);
            URI uri = URI.create(ruleMetadata.absoluteScriptSourceLocation());
            String scheme = uri.getScheme();
            String description;

            if ("file".equalsIgnoreCase(scheme)) {
                description = relativeFilePathResolver.resolveAsRelativePath(ruleMetadata.absoluteScriptSourceLocation());
            } else {
                description = uri.toString();
            }

            return new SourceLocation(uri, description, ruleMetadata.lineNumber(), ruleMetadata.columnNumber());
        }
    }
}
