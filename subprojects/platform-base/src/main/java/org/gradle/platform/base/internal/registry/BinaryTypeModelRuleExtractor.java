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
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.NoInputsModelAction;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;

import java.util.List;

public class BinaryTypeModelRuleExtractor extends TypeModelRuleExtractor<BinaryType, BinarySpec, BaseBinarySpec> {
    public BinaryTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("binary", BinarySpec.class, BaseBinarySpec.class, BinaryTypeBuilder.class, schemaStore);
    }

    @Override
    protected <P extends BinarySpec> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedBinaryTypeRule(ruleDefinition, type);
    }

    private static class DefaultBinaryTypeBuilder extends AbstractTypeBuilder<BinarySpec> implements BinaryTypeBuilder<BinarySpec> {
        private DefaultBinaryTypeBuilder(ModelSchema<? extends BinarySpec> schema) {
            super(BinaryType.class, schema);
        }
    }

    private class ExtractedBinaryTypeRule extends ExtractedTypeRule<DefaultBinaryTypeBuilder> {
        public ExtractedBinaryTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<? extends BinarySpec> publicType) {
            super(ruleDefinition, publicType);
        }

        @Override
        protected DefaultBinaryTypeBuilder createBuilder(ModelSchema<? extends BinarySpec> schema) {
            return new DefaultBinaryTypeBuilder(schema);
        }

        @Override
        protected ModelAction<?> createRegistrationAction(ModelSchema<? extends BinarySpec> schema, final DefaultBinaryTypeBuilder builder, final ModelType<? extends BaseBinarySpec> implModelType) {
            return NoInputsModelAction.of(ModelReference.of(BinarySpecFactory.class), ruleDefinition.getDescriptor(), new Action<BinarySpecFactory>() {
                @Override
                public void execute(BinarySpecFactory binaries) {
                    binaries.register(publicType, implModelType, builder.getInternalViews(), ruleDefinition.getDescriptor());
                }
            });
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        }
    }
}
