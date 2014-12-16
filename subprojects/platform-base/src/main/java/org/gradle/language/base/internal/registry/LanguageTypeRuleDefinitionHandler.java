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

package org.gradle.language.base.internal.registry;

import org.gradle.api.Action;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.platform.base.BaseLanguageSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.platform.base.internal.registry.AbstractTypeBuilder;
import org.gradle.platform.base.internal.registry.ComponentModelRuleDefinitionHandler;

public class LanguageTypeRuleDefinitionHandler extends ComponentModelRuleDefinitionHandler<LanguageType, LanguageSourceSet, BaseLanguageSourceSet> {

    public LanguageTypeRuleDefinitionHandler(final Instantiator instantiator) {
        super("language", LanguageSourceSet.class, BaseLanguageSourceSet.class, LanguageTypeBuilder.class, JavaReflectionUtil.factory(new DirectInstantiator(), DefaultLanguageTypeBuilder.class), new RegistrationAction(instantiator));
    }

    public static class DefaultLanguageTypeBuilder extends AbstractTypeBuilder<LanguageSourceSet> implements LanguageTypeBuilder<LanguageSourceSet> {
        public DefaultLanguageTypeBuilder() {
            super(LanguageType.class);
        }
    }

    private static class RegistrationAction implements Action<RegistrationContext<LanguageSourceSet, BaseLanguageSourceSet>> {
        private final Instantiator instantiator;

        public RegistrationAction(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        @Override
        public void execute(RegistrationContext<LanguageSourceSet, BaseLanguageSourceSet> languageSourceSetBaseLanguageSourceSetRegistrationContext) {

        }
    }
}
