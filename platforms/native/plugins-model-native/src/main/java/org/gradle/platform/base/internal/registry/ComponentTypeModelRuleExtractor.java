/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.inspect.AbstractExtractedModelRule;
import org.gradle.model.internal.inspect.AbstractMethodRuleAction;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodModelRuleApplicationContext;
import org.gradle.model.internal.inspect.MethodModelRuleExtractionContext;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.ModelRuleInvoker;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Collections.emptyList;

@SuppressWarnings("deprecation")
public class ComponentTypeModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<org.gradle.platform.base.ComponentType> {

    public static final ModelType<ComponentSpecFactory> COMPONENT_SPEC_FACTORY_CLASS = ModelType.of(ComponentSpecFactory.class);
    private static final ModelReference<ComponentSpecFactory> COMPONENT_SPEC_FACTORY_MODEL_REFERENCE = ModelReference.of(COMPONENT_SPEC_FACTORY_CLASS);
    private static final ModelType<org.gradle.platform.base.ComponentSpec> COMPONENT_SPEC_MODEL_TYPE = ModelType.of(org.gradle.platform.base.ComponentSpec.class);
    private static final ModelType<org.gradle.language.base.LanguageSourceSet> LANGUAGE_SOURCE_SET_MODEL_TYPE = ModelType.of(org.gradle.language.base.LanguageSourceSet.class);
    private static final ModelType<org.gradle.platform.base.BinarySpec> BINARY_SPEC_MODEL_TYPE = ModelType.of(org.gradle.platform.base.BinarySpec.class);
    private static final ModelType<org.gradle.platform.base.SourceComponentSpec> SOURCE_COMPONENT_SPEC_MODEL_TYPE = ModelType.of(org.gradle.platform.base.SourceComponentSpec.class);
    private static final ModelType<org.gradle.platform.base.VariantComponentSpec> VARIANT_COMPONENT_SPEC_MODEL_TYPE = ModelType.of(org.gradle.platform.base.VariantComponentSpec.class);

    private static class ComponentTypeRegistrationInfo {
        private final String modelName;
        private final ModelType<? extends org.gradle.platform.base.ComponentSpec> baseInterface;
        private final List<Class<?>> requiredPlugins;

        private ComponentTypeRegistrationInfo(String modelName, ModelType<? extends org.gradle.platform.base.ComponentSpec> baseInterface, Class<?> requiredPlugins) {
            this.modelName = modelName;
            this.baseInterface = baseInterface;
            this.requiredPlugins = ImmutableList.<Class<?>>of(requiredPlugins);
        }
    }

    private final ModelSchemaStore schemaStore;
    private final ModelReference<ComponentSpecFactory> registryRef;

    public ComponentTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        this.registryRef = COMPONENT_SPEC_FACTORY_MODEL_REFERENCE;
    }

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        validateIsVoidMethod(ruleDefinition, context);

        if (ruleDefinition.getReferences().size() != 1) {
            context.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), org.gradle.platform.base.TypeBuilder.class.getName()));
            return null;
        }

        ModelReference<?> subjectReference = ruleDefinition.getSubjectReference();
        ModelType<?> subjectType = subjectReference.getType();
        Class<?> rawSubjectType = subjectType.getRawClass();
        if (!rawSubjectType.equals(org.gradle.platform.base.TypeBuilder.class)) {
            context.add(ruleDefinition, String.format("A method %s must have a single parameter of type %s.", getDescription(), org.gradle.platform.base.TypeBuilder.class.getName()));
            return null;
        }

        List<ModelType<?>> typeVariables = subjectType.getTypeVariables();
        if (typeVariables.size() != 1) {
            context.add(ruleDefinition, String.format("Parameter of type %s must declare a type parameter.", rawSubjectType.getName()));
            return null;
        }

        ModelType<?> builtType = typeVariables.get(0);
        if (builtType.isWildcard()) {
            context.add(ruleDefinition, String.format("Type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", builtType.toString()));
            return null;
        }

        ComponentTypeRegistrationInfo info = componentTypeRegistrationInfoFor(builtType);
        if (info == null) {
            context.add(ruleDefinition, String.format("Type '%s' is not a subtype of '%s'.", builtType.toString(), COMPONENT_SPEC_MODEL_TYPE.toString()));
            return null;
        }

        return new ExtractedTypeRule(ruleDefinition, info, builtType.asSubtype(info.baseInterface));
    }

    private ComponentTypeRegistrationInfo componentTypeRegistrationInfoFor(ModelType<?> builtType) {
        if (LANGUAGE_SOURCE_SET_MODEL_TYPE.isAssignableFrom(builtType)) {
            return new ComponentTypeRegistrationInfo("language", LANGUAGE_SOURCE_SET_MODEL_TYPE, org.gradle.language.base.plugins.LanguageBasePlugin.class);
        }
        if (BINARY_SPEC_MODEL_TYPE.isAssignableFrom(builtType)) {
            return new ComponentTypeRegistrationInfo("binary", BINARY_SPEC_MODEL_TYPE, org.gradle.platform.base.plugins.BinaryBasePlugin.class);
        }
        if (COMPONENT_SPEC_MODEL_TYPE.isAssignableFrom(builtType)) {
            Class<?> requiredPlugin = SOURCE_COMPONENT_SPEC_MODEL_TYPE.isAssignableFrom(builtType) || VARIANT_COMPONENT_SPEC_MODEL_TYPE.isAssignableFrom(builtType)
                ? org.gradle.language.base.plugins.ComponentModelBasePlugin.class
                : org.gradle.platform.base.plugins.ComponentBasePlugin.class;
            return new ComponentTypeRegistrationInfo("component", COMPONENT_SPEC_MODEL_TYPE, requiredPlugin);
        }
        return null;
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
                throw new org.gradle.platform.base.InvalidModelException(String.format("Internal view %s must be an interface.", internalView.getName()));
            }
        }
    }

    private class ExtractedTypeRule extends AbstractExtractedModelRule {
        private final ComponentTypeRegistrationInfo info;
        private final ModelType<? extends org.gradle.platform.base.ComponentSpec> modelType;

        public ExtractedTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ComponentTypeRegistrationInfo info, ModelType<? extends org.gradle.platform.base.ComponentSpec> modelType) {
            super(ruleDefinition);
            this.info = info;
            this.modelType = modelType;
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return info.requiredPlugins;
        }

        @Override
        public void apply(final MethodModelRuleApplicationContext context, MutableModelNode target) {
            context.getRegistry().configure(
                ModelActionRole.Mutate,
                context.contextualize(new TypeRegistrationAction()));
        }

        private class TypeRegistrationAction extends AbstractMethodRuleAction<ComponentSpecFactory> {
            public TypeRegistrationAction() {
                super(registryRef, getRuleDefinition().getDescriptor());
            }

            @Override
            public List<? extends ModelReference<?>> getInputs() {
                return emptyList();
            }

            @Override
            protected void execute(ModelRuleInvoker<?> invoker, ComponentSpecFactory registry, List<ModelView<?>> inputs) {
                try {
                    ModelSchema<? extends org.gradle.platform.base.ComponentSpec> schema = schemaStore.getSchema(modelType);
                    TypeBuilderInternal<? extends org.gradle.platform.base.ComponentSpec> builder = new DefaultTypeBuilder<org.gradle.platform.base.ComponentSpec>(getAnnotationType(), schema);
                    invoker.invoke(builder);
                    ModelType<?> implModelType = determineImplementationType(builder);
                    registry.register(modelType, builder.getInternalViews(), implModelType, getDescriptor());
                } catch (org.gradle.platform.base.InvalidModelException e) {
                    throw invalidModelRule(getRuleDefinition(), info.modelName, e);
                }
            }

            private org.gradle.model.InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, String modelName, org.gradle.platform.base.InvalidModelException e) {
                StringBuilder sb = new StringBuilder();
                ruleDefinition.getDescriptor().describeTo(sb);
                sb.append(String.format(" is not a valid %s model rule method.", modelName));
                return new org.gradle.model.InvalidModelRuleDeclarationException(sb.toString(), e);
            }
        }
    }
}
