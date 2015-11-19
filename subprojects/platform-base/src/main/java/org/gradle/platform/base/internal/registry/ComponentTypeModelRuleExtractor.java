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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.List;
import java.util.Set;

public class ComponentTypeModelRuleExtractor extends TypeModelRuleExtractor<ComponentType, ComponentSpec, BaseComponentSpec> {
    public ComponentTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("component", ComponentSpec.class, BaseComponentSpec.class, ComponentTypeBuilder.class, schemaStore, new TypeBuilderFactory<ComponentSpec>() {
            @Override
            public TypeBuilderInternal<ComponentSpec> create(ModelSchema<? extends ComponentSpec> schema) {
                return new DefaultComponentTypeBuilder(schema);
            }
        });
    }

    @Override
    protected <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends ComponentSpec> type, TypeBuilderInternal<ComponentSpec> builder) {
        List<Class<?>> dependencies = ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        ModelType<? extends BaseComponentSpec> implementation = determineImplementationType(type, builder);
        ModelAction registrationAction = createRegistrationAction(type, implementation, builder.getInternalViews(), ruleDefinition.getDescriptor());
        return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, registrationAction);
    }

    public static class DefaultComponentTypeBuilder extends AbstractTypeBuilder<ComponentSpec> implements ComponentTypeBuilder<ComponentSpec> {
        public DefaultComponentTypeBuilder(ModelSchema<? extends ComponentSpec> schema) {
            super(ComponentType.class, schema);
        }
    }

    private <S extends ComponentSpec> ModelAction createRegistrationAction(final ModelType<S> publicType, final ModelType<? extends BaseComponentSpec> implementationType,
                                                                           final Set<Class<?>> internalViews, final ModelRuleDescriptor descriptor) {
        return NoInputsModelAction.of(ModelReference.of(ComponentSpecFactory.class), descriptor, new Action<ComponentSpecFactory>() {
            @Override
            public void execute(ComponentSpecFactory components) {
                components.register(publicType, implementationType, internalViews, descriptor);
            }
        });
    }
}
