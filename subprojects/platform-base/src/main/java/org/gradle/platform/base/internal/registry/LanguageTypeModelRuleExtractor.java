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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.NoInputsModelAction;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;

import java.util.List;

public class LanguageTypeModelRuleExtractor extends TypeModelRuleExtractor<LanguageType, LanguageSourceSet, BaseLanguageSourceSet> {
    public LanguageTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("language", LanguageSourceSet.class, BaseLanguageSourceSet.class, LanguageTypeBuilder.class, schemaStore);
    }

    @Override
    protected <P extends LanguageSourceSet> ExtractedModelRule createExtractedRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<P> type) {
        return new ExtractedLanguageTypeRule<P>(ruleDefinition, type);
    }

    private static class DefaultLanguageTypeBuilder<PUBLICTYPE extends LanguageSourceSet> extends AbstractTypeBuilder<PUBLICTYPE> implements LanguageTypeBuilder<PUBLICTYPE> {
        private String languageName;

        private DefaultLanguageTypeBuilder(ModelSchema<PUBLICTYPE> schema) {
            super(LanguageType.class, schema);
        }

        @Override
        public void setLanguageName(String languageName) {
            this.languageName = languageName;
        }

        public String getLanguageName() {
            return languageName;
        }
    }

    private class ExtractedLanguageTypeRule<PUBLICTYPE extends LanguageSourceSet> extends ExtractedTypeRule<PUBLICTYPE, DefaultLanguageTypeBuilder<PUBLICTYPE>> {
        public ExtractedLanguageTypeRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<PUBLICTYPE> publicType) {
            super(ruleDefinition, publicType);
        }

        @Override
        protected DefaultLanguageTypeBuilder<PUBLICTYPE> createBuilder(ModelSchema<PUBLICTYPE> schema) {
            return new DefaultLanguageTypeBuilder<PUBLICTYPE>(schema);
        }

        @Override
        protected ModelAction<?> createRegistrationAction(ModelSchema<PUBLICTYPE> schema, final DefaultLanguageTypeBuilder<PUBLICTYPE> builder, final ModelType<? extends BaseLanguageSourceSet> implModelType) {
            final String languageName = builder.getLanguageName();
            if (!ModelType.of(LanguageSourceSet.class).equals(publicType) && StringUtils.isEmpty(languageName)) {
                throw new InvalidModelException(String.format("Language type '%s' cannot be registered without a language name.", publicType));
            }
            return NoInputsModelAction.of(ModelReference.of(LanguageSourceSetFactory.class), ruleDefinition.getDescriptor(), new Action<LanguageSourceSetFactory>() {
                @Override
                public void execute(LanguageSourceSetFactory sourceSetFactory) {
                    sourceSetFactory.register(languageName, publicType, builder.getInternalViews(), implModelType, ruleDefinition.getDescriptor());
                }
            });
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return ImmutableList.of(ComponentModelBasePlugin.class);
        }
    }
}
