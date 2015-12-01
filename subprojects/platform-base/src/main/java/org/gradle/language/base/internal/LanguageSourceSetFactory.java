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

package org.gradle.language.base.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.internal.core.BaseInstanceFactory;
import org.gradle.model.internal.core.InstanceFactory;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class LanguageSourceSetFactory extends BaseInstanceFactory<LanguageSourceSet> {

    private final FileResolver fileResolver;

    public LanguageSourceSetFactory(String displayName, FileResolver fileResolver) {
        super(displayName, LanguageSourceSet.class, BaseLanguageSourceSet.class);
        this.fileResolver = fileResolver;
    }

    public <T extends LanguageSourceSet, V extends T> void register(ModelType<T> type, final ModelType<V> implementationType, ModelRuleDescriptor ruleDescriptor) {
        InstanceFactory.TypeRegistrationBuilder<T> registration = register(type, ruleDescriptor);

        registration.withImplementation(implementationType, new InstanceFactory.ImplementationFactory<T>() {
            @Override
            public T create(ModelType<? extends T> publicType, String sourceSetName, MutableModelNode modelNode) {
                return BaseLanguageSourceSet.create(publicType.getConcreteClass(), implementationType.getConcreteClass(), sourceSetName, determineParentName(modelNode), fileResolver);
            }
        });
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


}
