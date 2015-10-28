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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Nullable;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class LanguageSourceSetNodeInitializer implements NodeInitializer {

    private final LanguageRegistry languageRegistry;
    private final ModelType<?> type;

    public LanguageSourceSetNodeInitializer(LanguageRegistry languageRegistry, ModelType<?> type) {
        this.languageRegistry = languageRegistry;
        this.type = type;
    }

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        LanguageSourceSet languageSourceSet = createLanguageSourceSet(modelNode.getPath().getName(), type.getConcreteClass());
        modelNode.setPrivateData((ModelType<? super LanguageSourceSet>) type, languageSourceSet);
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.singletonList(UnmanagedModelProjection.of(type));
    }

    @Nullable
    @Override
    public ModelAction getProjector(ModelPath path, ModelRuleDescriptor descriptor) {
        return null;
    }

    @SuppressWarnings("unchecked")
    private <U> U createLanguageSourceSet(String name, Class<?> type) {
        NamedDomainObjectFactory<? extends LanguageSourceSet> sourceSetFactory = findSourceSetFactory(type, name);
        return (U) type.cast(sourceSetFactory.create(name));
    }

    private <U extends LanguageSourceSet> NamedDomainObjectFactory<? extends LanguageSourceSet> findSourceSetFactory(Class<?> type, String name) {
        for (LanguageRegistration<?> languageRegistration : languageRegistry) {
            Class<? extends LanguageSourceSet> sourceSetType = languageRegistration.getSourceSetType();
            if (type.equals(sourceSetType)) {
                return languageRegistration.getSourceSetFactory(name);
            }
        }
        return null;
    }
}
