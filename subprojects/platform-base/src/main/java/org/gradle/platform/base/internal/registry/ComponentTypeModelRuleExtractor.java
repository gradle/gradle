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
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;

import java.util.List;

public class ComponentTypeModelRuleExtractor extends TypeModelRuleExtractor<ComponentType, ComponentSpec, BaseComponentSpec> {
    private static final ModelType<ComponentSpecFactory> COMPONENT_SPEC_FACTORY_MODEL_TYPE = ModelType.of(ComponentSpecFactory.class);

    public ComponentTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("component", ComponentSpec.class, BaseComponentSpec.class, TypeBuilder.class, schemaStore);
    }

    @Override
    protected <P extends ComponentSpec> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedComponentTypeRule<P>(ruleDefinition, type);
    }

    private class ExtractedComponentTypeRule<PUBLICTYPE extends ComponentSpec> extends ExtractedTypeRule<PUBLICTYPE, ComponentSpecFactory> {
        public ExtractedComponentTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            super(ruleDefinition, publicType, COMPONENT_SPEC_FACTORY_MODEL_TYPE);
        }

        @Override
        protected DefaultTypeBuilder<PUBLICTYPE> createBuilder(ModelSchema<PUBLICTYPE> schema) {
            return new DefaultTypeBuilder<PUBLICTYPE>(ComponentType.class, schema);
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(ComponentModelBasePlugin.class);
        }
    }
}
