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
import org.gradle.internal.BiAction;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.model.dsl.internal.inputs.PotentialInput;
import org.gradle.model.dsl.internal.inputs.PotentialInputs;
import org.gradle.model.internal.core.DeferredModelAction;
import org.gradle.model.internal.core.InputUsingModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.ClosureBackedAction;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class ClosureBackedRuleFactory {
    private static final ModelType<ManagedInstance> MANAGED_INSTANCE_TYPE = ModelType.of(ManagedInstance.class);
    private final Transformer<SourceLocation, TransformedClosure> ruleLocationExtractor;

    public ClosureBackedRuleFactory(RelativeFilePathResolver relativeFilePathResolver) {
        this.ruleLocationExtractor = new RelativePathSourceLocationTransformer(relativeFilePathResolver);
    }

    /**
     * Used by generated code. See {@link RuleVisitor}.
     */
    @SuppressWarnings("unused")
    public static Object decorate(@Nullable ClosureBackedRuleFactory factory, Closure<?> closure) {
        return factory == null ? closure : factory.toAction(Object.class, closure);
    }

    public <T> DeferredModelAction toAction(final Class<T> subjectType, final Closure<?> closure) {
        final TransformedClosure transformedClosure = (TransformedClosure) closure;
        SourceLocation sourceLocation = ruleLocationExtractor.transform(transformedClosure);
        final ModelRuleDescriptor descriptor = sourceLocation.asDescriptor();

        return new DeferredModelAction() {
            @Override
            public ModelRuleDescriptor getDescriptor() {
                return descriptor;
            }

            @Override
            public void execute(MutableModelNode node, ModelActionRole role) {
                final boolean supportsNestedRules = node.canBeViewedAs(MANAGED_INSTANCE_TYPE);
                InputReferences inputs = transformedClosure.inputReferences();
                List<InputReference> inputReferences = supportsNestedRules ? inputs.getOwnReferences() : inputs.getAllReferences();
                final Map<String, PotentialInput> inputValues = Maps.newLinkedHashMap();
                List<ModelReference<?>> inputModelReferences = Lists.newArrayList();

                for (InputReference inputReference : inputReferences) {
                    String description = "@ line " + inputReference.getLineNumber();
                    String path = inputReference.getPath();
                    if (!inputValues.containsKey(path)) {
                        inputValues.put(path, new PotentialInput(inputModelReferences.size()));
                        inputModelReferences.add(ModelReference.untyped(ModelPath.path(path), description));
                    }
                }

                node.applyToSelf(role, InputUsingModelAction.of(ModelReference.of(node.getPath(), subjectType), descriptor, inputModelReferences, new BiAction<T, List<ModelView<?>>>() {
                    @Override
                    public void execute(T t, List<ModelView<?>> modelViews) {
                        // Make a copy of the closure, attach inputs and execute
                        Closure<?> cloned = closure.rehydrate(null, closure.getThisObject(), closure.getThisObject());
                        ((TransformedClosure) cloned).makeRule(new PotentialInputs(modelViews, inputValues), supportsNestedRules ? ClosureBackedRuleFactory.this : null);
                        ClosureBackedAction.execute(t, cloned);
                    }
                }));
            }
        };
    }

    private static class RelativePathSourceLocationTransformer implements Transformer<SourceLocation, TransformedClosure> {
        private final RelativeFilePathResolver relativeFilePathResolver;

        public RelativePathSourceLocationTransformer(RelativeFilePathResolver relativeFilePathResolver) {
            this.relativeFilePathResolver = relativeFilePathResolver;
        }

        // TODO given that all the closures are from the same file, we should do the relativising once.
        //      that would entail adding location information to the model {} outer closure.
        @Override
        public SourceLocation transform(TransformedClosure closure) {
            SourceLocation sourceLocation = closure.sourceLocation();
            URI uri = sourceLocation.getUri();
            String scheme = uri.getScheme();
            String description;

            if ("file".equalsIgnoreCase(scheme)) {
                description = relativeFilePathResolver.resolveAsRelativePath(uri);
            } else {
                description = uri.toString();
            }

            return new SourceLocation(uri, description, sourceLocation.getExpression(), sourceLocation.getLineNumber(), sourceLocation.getColumnNumber());
        }
    }
}
