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
import org.gradle.model.internal.inspect.ExtractedModelAction;
import org.gradle.model.internal.inspect.ExtractedModelRule;
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
    protected <P extends ComponentSpec, I extends BaseComponentSpec> ExtractedModelRule createRegistration(
        final MethodRuleDefinition<?, ?> ruleDefinition,
        final ModelType<P> publicModelType, final ModelType<I> implModelType,
        final TypeBuilderInternal<ComponentSpec> builder
    ) {
        ModelAction<ComponentSpecFactory> regAction = NoInputsModelAction.of(ModelReference.of(ComponentSpecFactory.class), ruleDefinition.getDescriptor(), new Action<ComponentSpecFactory>() {
            @Override
            public void execute(ComponentSpecFactory components) {
                components.register(publicModelType, implModelType, builder.getInternalViews(), ruleDefinition.getDescriptor());
            }
        });
        return new ExtractedModelAction(ModelActionRole.Defaults, ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class), regAction);
    }

    private static class DefaultComponentTypeBuilder extends AbstractTypeBuilder<ComponentSpec> implements ComponentTypeBuilder<ComponentSpec> {
        private DefaultComponentTypeBuilder(ModelSchema<? extends ComponentSpec> schema) {
            super(ComponentType.class, schema);
        }
    }
}
