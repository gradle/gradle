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

package org.gradle.platform.base.internal.registry;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Nullable;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.*;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.typeregistration.BaseInstanceFactory;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public abstract class TypeModelRuleExtractor<ANNOTATION extends Annotation, TYPE, REGISTRY extends BaseInstanceFactory<? super TYPE>> extends AbstractAnnotationDrivenComponentModelRuleExtractor<ANNOTATION> {
    private final String modelName;
    private final ModelType<TYPE> baseInterface;
    private final ModelReference<REGISTRY> registryRef;
    private final ModelSchemaStore schemaStore;

    public TypeModelRuleExtractor(String modelName, Class<TYPE> baseInterface, ModelReference<REGISTRY> registryRef, ModelSchemaStore schemaStore) {
        this.modelName = modelName;
        this.registryRef = registryRef;
        this.schemaStore = schemaStore;
        this.baseInterface = ModelType.of(baseInterface);
    }

    protected abstract List<? extends Class<?>> getPluginsRequiredForClass(Class<? extends TYPE> publicType);

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(final MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        ModelType<? extends TYPE> type = readType(ruleDefinition, context);
        if (context.hasProblems()) {
            return null;
        }
        return createExtractedRule(ruleDefinition, type);
    }

    private <P extends TYPE> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedTypeRule<P>(ruleDefinition, type);
    }

    private ModelType<? extends TYPE> readType(MethodRuleDefinition<?, ?> ruleDefinition, RuleSourceValidationProblemCollector problems) {
        validateIsVoidMethod(ruleDefinition, problems);
        if (ruleDefinition.getReferences().size() != 1) {
            problems.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), TypeBuilder.class.getName()));
            return null;
        }

        ModelReference<?> subjectReference = ruleDefinition.getSubjectReference();
        ModelType<?> subjectType = subjectReference.getType();
        Class<?> rawSubjectType = subjectType.getRawClass();
        if (!rawSubjectType.equals(TypeBuilder.class)) {
            problems.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), TypeBuilder.class.getName()));
            return null;
        }

        List<ModelType<?>> typeVariables = subjectType.getTypeVariables();
        if (typeVariables.size() != 1) {
            problems.add(ruleDefinition, String.format("Parameter of type %s must declare a type parameter.", rawSubjectType.getName()));
            return null;
        }

        ModelType<?> builtType = typeVariables.get(0);
        if (builtType.isWildcard()) {
            problems.add(ruleDefinition, String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", StringUtils.capitalize(modelName), builtType.toString()));
            return null;
        }
        if (!baseInterface.isAssignableFrom(builtType)) {
            problems.add(ruleDefinition, String.format("%s type '%s' is not a subtype of '%s'.", StringUtils.capitalize(modelName), builtType.toString(), baseInterface.toString()));
            return null;
        }

        return builtType.asSubtype(baseInterface);
    }

    private InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private ModelType<?> determineImplementationType(TypeBuilderInternal<?> builder) {
        validateInternalViewsAreInterfaces(builder);

        Class<?> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }

        return ModelType.of(implementation);
    }

    private void validateInternalViewsAreInterfaces(TypeBuilderInternal<?> builder) {
        for (Class<?> internalView : builder.getInternalViews()) {
            if (!internalView.isInterface()) {
                throw new InvalidModelException(String.format("Internal view %s must be an interface.", internalView.getName()));
            }
        }
    }

    private class ExtractedTypeRule<PUBLICTYPE extends TYPE> implements ExtractedModelRule {
        private final MethodRuleDefinition<?, ?> ruleDefinition;
        private final ModelType<PUBLICTYPE> publicType;
        private final List<? extends Class<?>> plugins;

        public ExtractedTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            this.ruleDefinition = ruleDefinition;
            this.publicType = publicType;
            this.plugins = getPluginsRequiredForClass(publicType.getConcreteClass());
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return plugins;
        }

        @Override
        public void apply(final MethodModelRuleApplicationContext context, MutableModelNode target) {
            context.getRegistry().configure(ModelActionRole.Mutate,
                    context.contextualize(new TypeRegistrationAction(registryRef, ruleDefinition.getDescriptor())));
        }

        @Override
        public MethodRuleDefinition<?, ?> getRuleDefinition() {
            return ruleDefinition;
        }

        private class TypeRegistrationAction extends AbstractMethodRuleAction<REGISTRY> {
            public TypeRegistrationAction(ModelReference<REGISTRY> subject, ModelRuleDescriptor descriptor) {
                super(subject, descriptor);
            }

            @Override
            public List<? extends ModelReference<?>> getInputs() {
                return Collections.emptyList();
            }

            @Override
            protected void execute(ModelRuleInvoker<?> invoker, REGISTRY registry, List<ModelView<?>> inputs) {
                try {
                    ModelSchema<PUBLICTYPE> schema = schemaStore.getSchema(publicType);
                    TypeBuilderInternal<PUBLICTYPE> builder = new DefaultTypeBuilder<PUBLICTYPE>(getAnnotationType(), schema);
                    invoker.invoke(builder);
                    ModelType<?> implModelType = determineImplementationType(builder);
                    registry.register(publicType, builder.getInternalViews(), implModelType, ruleDefinition.getDescriptor());
                } catch (InvalidModelException e) {
                    throw invalidModelRule(ruleDefinition, e);
                }
            }
        }
    }
}
