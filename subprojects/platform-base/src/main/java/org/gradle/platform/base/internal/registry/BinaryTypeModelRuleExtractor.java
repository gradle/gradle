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
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.plugins.BinaryBasePlugin;

import java.util.List;

public class BinaryTypeModelRuleExtractor extends TypeModelRuleExtractor<BinaryType, BinarySpec, BaseBinarySpec> {
    private static final ModelType<BinarySpecFactory> BINARY_SPEC_FACTORY_MODEL_TYPE = ModelType.of(BinarySpecFactory.class);

    public BinaryTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("binary", BinarySpec.class, BaseBinarySpec.class, TypeBuilder.class, schemaStore);
    }

    @Override
    protected <P extends BinarySpec> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedBinaryTypeRule<P>(ruleDefinition, type);
    }

    private class ExtractedBinaryTypeRule<PUBLICTYPE extends BinarySpec> extends ExtractedTypeRule<PUBLICTYPE, BinarySpecFactory> {
        public ExtractedBinaryTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            super(ruleDefinition, publicType, BINARY_SPEC_FACTORY_MODEL_TYPE);
        }

        @Override
        protected DefaultTypeBuilder<PUBLICTYPE> createBuilder(ModelSchema<PUBLICTYPE> schema) {
            return new DefaultTypeBuilder<PUBLICTYPE>(BinaryType.class, schema);
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(BinaryBasePlugin.class);
        }
    }
}
