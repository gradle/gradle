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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class LanguageSourceSetNodeInitializer implements NodeInitializer {

    private final ModelType<?> type;

    public LanguageSourceSetNodeInitializer(ModelType<?> type) {
        this.type = type;
    }

    @Override
    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
            .put(ModelActionRole.Discover, AddProjectionsAction.of(subject, descriptor, UnmanagedModelProjection.of(type)))
            .put(ModelActionRole.Create, DirectNodeInputUsingModelAction.of(subject, descriptor,
                ModelReference.of(LanguageRegistry.class),
                new BiAction<MutableModelNode, LanguageRegistry>() {
                    @Override
                    public void execute(MutableModelNode modelNode, LanguageRegistry languageRegistry) {
                        String sourceSetName = modelNode.getPath().getName();
                        String parentName = determineParentName(modelNode);
                        LanguageSourceSet languageSourceSet = createLanguageSourceSet(parentName, sourceSetName, type.getConcreteClass(), languageRegistry);
                        modelNode.setPrivateData(Cast.<ModelType<? super LanguageSourceSet>>uncheckedCast(type), languageSourceSet);
                    }
                }
            ))
            .build();
    }

    private String determineParentName(MutableModelNode modelNode) {
        MutableModelNode grandparentNode = modelNode.getParent().getParent();
        // Special case handling of a LanguageSourceSet that is part of `ComponentSpec.sources` or `BinarySpec.sources`
        if (grandparentNode != null) {
            if (grandparentNode.getPrivateData() instanceof BaseBinarySpec) {
                // TODO:DAZ Should be using binary.projectScopedName for uniqueness
                BaseBinarySpec binarySpecInternal = (BaseBinarySpec) grandparentNode.getPrivateData();
                return binarySpecInternal.getComponent() == null ? binarySpecInternal.getName() : binarySpecInternal.getComponent().getName();
            }
            if (grandparentNode.getPrivateData() instanceof ComponentSpecInternal) {
                return ((ComponentSpecInternal) grandparentNode.getPrivateData()).getName();
            }
        }

        return modelNode.getParent().getPath().getName();
    }

    @SuppressWarnings("unchecked")
    private <U> U createLanguageSourceSet(String parentName, String name, Class<?> type, LanguageRegistry languageRegistry) {
        NamedDomainObjectFactory<? extends LanguageSourceSet> sourceSetFactory = findSourceSetFactory(type, parentName, languageRegistry);
        return (U) type.cast(sourceSetFactory.create(name));
    }

    private NamedDomainObjectFactory<? extends LanguageSourceSet> findSourceSetFactory(Class<?> type, String parentName, LanguageRegistry languageRegistry) {
        for (LanguageRegistration<?> languageRegistration : languageRegistry) {
            Class<? extends LanguageSourceSet> sourceSetType = languageRegistration.getSourceSetType();
            if (type.equals(sourceSetType)) {
                return languageRegistration.getSourceSetFactory(parentName);
            }
        }
        return null;
    }
}
