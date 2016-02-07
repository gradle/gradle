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
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.TypeBuilder;

import java.util.List;

public class LanguageTypeModelRuleExtractor extends TypeModelRuleExtractor<LanguageType, LanguageSourceSet, BaseLanguageSourceSet> {
    private static final ModelType<LanguageSourceSetFactory> LANGUAGE_SOURCE_SET_FACTORY_MODEL_TYPE = ModelType.of(LanguageSourceSetFactory.class);

    public LanguageTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("language", LanguageSourceSet.class, BaseLanguageSourceSet.class, TypeBuilder.class, schemaStore);
    }

    @Override
    protected <P extends LanguageSourceSet> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedLanguageTypeRule<P>(ruleDefinition, type);
    }

    private class ExtractedLanguageTypeRule<PUBLICTYPE extends LanguageSourceSet> extends ExtractedTypeRule<PUBLICTYPE, LanguageSourceSetFactory> {
        public ExtractedLanguageTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            super(ruleDefinition, publicType, LANGUAGE_SOURCE_SET_FACTORY_MODEL_TYPE);
        }

        @Override
        protected DefaultTypeBuilder<PUBLICTYPE> createBuilder(ModelSchema<PUBLICTYPE> schema) {
            return new DefaultTypeBuilder<PUBLICTYPE>(LanguageType.class, schema);
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(LanguageBasePlugin.class);
        }
    }
}
