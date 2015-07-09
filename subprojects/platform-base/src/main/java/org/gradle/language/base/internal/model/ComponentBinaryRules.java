/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.internal.model;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

/**
 * Initializes binaries as they are added to components.
 *
 * Note: It's applied to components instead of binaries straight, because binaries are accessible
 * via multiple paths, and thus rules are applied to them multiple times.
 */
@SuppressWarnings("unused")
public class ComponentBinaryRules extends RuleSource {

    @Defaults
    void initializeBinarySourceSets(final ComponentSpec component, final LanguageRegistry languageRegistry) {
        component.getBinaries().beforeEach(new Action<BinarySpec>() {
            @Override
            public void execute(BinarySpec binary) {
                for (LanguageRegistration<?> languageRegistration : languageRegistry) {
                    // TODO - allow view as internal type and remove the cast
                    registerLanguageSourceSets((BinarySpecInternal) binary, component.getName(), languageRegistration);
                }
                addComponentSourceSetsToBinaryInputs(binary, component);
            }

            private <U extends LanguageSourceSet> void registerLanguageSourceSets(BinarySpecInternal binary, String componentName, LanguageRegistration<U> languageRegistration) {
                NamedDomainObjectFactory<? extends U> sourceSetFactory = languageRegistration.getSourceSetFactory(componentName);
                binary.getEntityInstantiator().registerFactory(languageRegistration.getSourceSetType(), sourceSetFactory);
            }

            private void addComponentSourceSetsToBinaryInputs(BinarySpec binary, ComponentSpec component) {
                binary.getInputs().addAll(component.getSources().values());
            }
        });
    }
}
