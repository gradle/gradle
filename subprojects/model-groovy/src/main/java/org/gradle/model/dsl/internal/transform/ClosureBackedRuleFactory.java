/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.dsl.internal.transform;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.BiAction;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.model.dsl.internal.inputs.PotentialInput;
import org.gradle.model.dsl.internal.inputs.PotentialInputs;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ClosureBackedRuleFactory {
    private final Transformer<SourceLocation, ? super Closure<?>> ruleLocationExtractor;

    public ClosureBackedRuleFactory(RelativeFilePathResolver relativeFilePathResolver) {
        this.ruleLocationExtractor = new RelativePathSourceLocationTransformer(relativeFilePathResolver);
    }

    public <T> DeferredModelAction toAction(final ModelPath subjectPath, final Class<T> subjectType, final Closure<?> closure) {
        SourceLocation sourceLocation = ruleLocationExtractor.transform(closure);
        final ModelRuleDescriptor descriptor = sourceLocation.asDescriptor("model." + subjectPath);

        return new DeferredModelAction() {
            @Override
            public ModelRuleDescriptor getDescriptor() {
                return descriptor;
            }

            @Override
            public void execute(MutableModelNode node, ModelActionRole role) {
                TransformedClosure transformedClosure = (TransformedClosure) closure;
                final boolean supportsNestedRules = node.canBeViewedAs(ModelType.of(ManagedInstance.class));
                InputReferences inputs = transformedClosure.inputReferences();
                List<InputReference> inputReferences = supportsNestedRules ? inputs.getOwnReferences() : inputs.getAllReferences();
                final Map<String, PotentialInput> inputValues = Maps.newLinkedHashMap();
                List<ModelReference<?>> inputModelReferences = Lists.newArrayList();

                for (InputReference inputReference : inputReferences) {
                    String description = String.format("@ line %d", inputReference.getLineNumber());
                    String path = inputReference.getPath();
                    if (!inputValues.containsKey(path)) {
                        inputValues.put(path, new PotentialInput(inputModelReferences.size()));
                        inputModelReferences.add(ModelReference.untyped(ModelPath.path(path), description));
                    }
                }

                node.applyToSelf(role, InputUsingModelAction.of(ModelReference.of(subjectPath, subjectType), descriptor, inputModelReferences, new BiAction<T, List<ModelView<?>>>() {
                    @Override
                    public void execute(T t, List<ModelView<?>> modelViews) {
                        Closure<?> cloned = closure.rehydrate(null, closure.getThisObject(), closure.getThisObject());
                        ((TransformedClosure) cloned).makeRule(new PotentialInputs(modelViews, inputValues), supportsNestedRules);
                        ClosureBackedAction.execute(t, cloned);
                    }
                }));

            }
        };
    }

    private static class RelativePathSourceLocationTransformer implements Transformer<SourceLocation, Closure<?>> {
        private final RelativeFilePathResolver relativeFilePathResolver;

        public RelativePathSourceLocationTransformer(RelativeFilePathResolver relativeFilePathResolver) {
            this.relativeFilePathResolver = relativeFilePathResolver;
        }

        private static RuleMetadata getRuleMetadata(Closure<?> closure) {
            RuleMetadata ruleMetadata = closure.getClass().getAnnotation(RuleMetadata.class);
            if (ruleMetadata == null) {
                throw new IllegalStateException(String.format("Expected %s annotation to be used on the argument closure.", RuleMetadata.class.getName()));
            }
            return ruleMetadata;
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
