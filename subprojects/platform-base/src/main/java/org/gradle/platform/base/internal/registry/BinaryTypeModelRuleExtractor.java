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
        return new ExtractedBinaryTypeRule<P>(ruleDefinition, type);
    }

    private static class DefaultBinaryTypeBuilder<PUBLICTYPE extends BinarySpec> extends AbstractTypeBuilder<PUBLICTYPE> implements BinaryTypeBuilder<PUBLICTYPE> {
        private DefaultBinaryTypeBuilder(ModelSchema<PUBLICTYPE> schema) {
            super(BinaryType.class, schema);
        }
    }

    private class ExtractedBinaryTypeRule<PUBLICTYPE extends BinarySpec> extends ExtractedTypeRule<PUBLICTYPE, DefaultBinaryTypeBuilder<PUBLICTYPE>, BinarySpecFactory> {
        public ExtractedBinaryTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            super(ruleDefinition, publicType);
        }

        @Override
        protected DefaultBinaryTypeBuilder<PUBLICTYPE> createBuilder(ModelSchema<PUBLICTYPE> schema) {
            return new DefaultBinaryTypeBuilder<PUBLICTYPE>(schema);
        }

        @Override
        protected Class<BinarySpecFactory> getRegistryType() {
            return BinarySpecFactory.class;
        }

        @Override
        protected void register(BinarySpecFactory binaries, ModelSchema<PUBLICTYPE> schema, DefaultBinaryTypeBuilder<PUBLICTYPE> builder, ModelType<? extends BaseBinarySpec> implModelType) {
            binaries.register(publicType, implModelType, builder.getInternalViews(), ruleDefinition.getDescriptor());
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(ComponentModelBasePlugin.class);
        }
    }
}
