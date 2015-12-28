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
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.*;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.lang.annotation.Annotation;

public abstract class TypeModelRuleExtractor<ANNOTATION extends Annotation, TYPE, BASEIMPL extends TYPE> extends AbstractAnnotationDrivenComponentModelRuleExtractor<ANNOTATION> {
    private final String modelName;
    private final ModelType<TYPE> baseInterface;
    private final ModelType<BASEIMPL> baseImplementation;
    private final ModelType<?> builderInterface;
    private final ModelSchemaStore schemaStore;

    public TypeModelRuleExtractor(String modelName, Class<TYPE> baseInterface, Class<BASEIMPL> baseImplementation, Class<?> builderInterface, ModelSchemaStore schemaStore) {
        this.modelName = modelName;
        this.schemaStore = schemaStore;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
        this.builderInterface = ModelType.of(builderInterface);
    }

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(final MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        ModelType<? extends TYPE> type = readType(ruleDefinition, context);
        if (context.hasProblems()) {
            return null;
        }
        return createExtractedRule(ruleDefinition, type);
    }

    /**
     * Create model type registration.
     * @param <P> Public parameterized type
     */
    protected abstract <P extends TYPE> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type);

    protected ModelType<? extends TYPE> readType(MethodRuleDefinition<?, ?> ruleDefinition, ValidationProblemCollector problems) {
        validateIsVoidMethod(ruleDefinition, problems);
        if (ruleDefinition.getReferences().size() != 1) {
            problems.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), builderInterface.toString()));
            return null;
        }

        ModelReference<?> subjectReference = ruleDefinition.getSubjectReference();
        ModelType<?> builder = subjectReference.getType();
        if (!builderInterface.isAssignableFrom(builder)) {
            problems.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), builderInterface.toString()));
            return null;
        }
        if (builder.getTypeVariables().size() != 1) {
            problems.add(ruleDefinition, String.format("Parameter of type %s must declare a type parameter.", builderInterface.toString()));
            return null;
        }

        ModelType<?> subType = builder.getTypeVariables().get(0);

        if (subType.isWildcard()) {
            problems.add(ruleDefinition, String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", StringUtils.capitalize(modelName), subType.toString()));
            return null;
        }
        if (!baseInterface.isAssignableFrom(subType)) {
            problems.add(ruleDefinition, String.format("%s type '%s' is not a subtype of '%s'.", StringUtils.capitalize(modelName), subType.toString(), baseInterface.toString()));
            return null;
        }

        return subType.asSubtype(baseInterface);
    }

    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected ModelType<? extends BASEIMPL> determineImplementationType(ModelType<? extends TYPE> type, TypeBuilderInternal<? extends TYPE> builder) {
        for (Class<?> internalView : builder.getInternalViews()) {
            if (!internalView.isInterface()) {
                throw new InvalidModelException(String.format("Internal view %s must be an interface.", internalView.getName()));
            }
        }

        Class<? extends TYPE> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }

        ModelType<? extends TYPE> implementationType = ModelType.of(implementation);

        if (!baseImplementation.isAssignableFrom(implementationType)) {
            throw new InvalidModelException(String.format("%s implementation %s must extend %s.", StringUtils.capitalize(modelName), implementationType, baseImplementation));
        }

        ModelType<? extends BASEIMPL> asSubclass = implementationType.asSubtype(baseImplementation);
        if (!type.isAssignableFrom(asSubclass)) {
            throw new InvalidModelException(String.format("%s implementation %s must implement %s.", StringUtils.capitalize(modelName), asSubclass, type));
        }

        for (Class<?> internalView : builder.getInternalViews()) {
            if (!internalView.isAssignableFrom(implementation)) {
                throw new InvalidModelException(String.format("%s implementation %s must implement internal view %s.", StringUtils.capitalize(modelName), asSubclass, internalView.getName()));
            }
        }

        try {
            asSubclass.getRawClass().getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidModelException(String.format("%s implementation %s must have public default constructor.", StringUtils.capitalize(modelName), asSubclass));
        }

        return asSubclass;
    }

    protected abstract class ExtractedTypeRule<PUBLICTYPE extends TYPE, BUILDER extends TypeBuilderInternal<PUBLICTYPE>> implements ExtractedModelRule {
        protected final MethodRuleDefinition<?, ?> ruleDefinition;
        protected final ModelType<PUBLICTYPE> publicType;

        public ExtractedTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            this.ruleDefinition = ruleDefinition;
            this.publicType = publicType;
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return ruleDefinition.getDescriptor();
        }

        @Override
        public void apply(MethodModelRuleApplicationContext context, MutableModelNode target) {
            ModelAction<?> action;
            try {
                ModelSchema<PUBLICTYPE> schema = schemaStore.getSchema(publicType);
                BUILDER builder = createBuilder(schema);
                ruleDefinition.getRuleInvoker().invoke(builder);
                ModelType<? extends BASEIMPL> implModelType = determineImplementationType(publicType, builder);
                action = createRegistrationAction(schema, builder, implModelType);
            } catch (InvalidModelException e) {
                throw invalidModelRule(ruleDefinition, e);
            }
            context.getRegistry().configure(ModelActionRole.Defaults, action, target.getPath());
        }

        protected abstract BUILDER createBuilder(ModelSchema<PUBLICTYPE> schema);

        protected abstract ModelAction<?> createRegistrationAction(ModelSchema<PUBLICTYPE> schema, BUILDER builder, ModelType<? extends BASEIMPL> implModelType);
    }
}
