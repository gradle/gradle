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
import org.gradle.api.DomainObjectSet;
import org.gradle.internal.BiAction;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.StandardDescriptorFactory;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;

import java.util.List;

public class ComponentSpecInitializationAction implements BiAction<MutableModelNode, ComponentSpec> {
    @Override
    public void execute(MutableModelNode componentModelNode, ComponentSpec componentSpec) {
        ModelType<DomainObjectSet<LanguageSourceSet>> sourceType = new ModelType<DomainObjectSet<LanguageSourceSet>>() {
        };
        final String sourceNodeName = "source";
        ModelReference<DomainObjectSet<LanguageSourceSet>> sourceReference = ModelReference.of(componentModelNode.getPath().child(sourceNodeName), sourceType);
        String containerCreatorDescriptor = new StandardDescriptorFactory(componentModelNode.getDescriptor()).transform(sourceNodeName);

        final StandardDescriptorFactory itemCreatorDescriptorFactory = new StandardDescriptorFactory(containerCreatorDescriptor);

        final DomainObjectSet<LanguageSourceSet> source = componentSpec.getSource();
        componentModelNode.addLink(
                ModelCreators.bridgedInstance(sourceReference, source, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(final MutableModelNode sourceModelNode, List<ModelView<?>> modelViews) {
                        source.all(new Action<LanguageSourceSet>() {
                            @Override
                            public void execute(LanguageSourceSet languageSourceSet) {
                                String name = languageSourceSet.getName();
                                ModelType<LanguageSourceSet> itemType = ModelType.typeOf(languageSourceSet);
                                ModelReference<LanguageSourceSet> itemReference = ModelReference.of(sourceModelNode.getPath().child(name), itemType);
                                ModelCreator itemCreator = ModelCreators.bridgedInstance(itemReference, languageSourceSet)
                                        .descriptor(itemCreatorDescriptorFactory.transform(name))
                                        .build();

                                sourceModelNode.addLink(itemCreator);
                            }
                        });
                    }
                })
                        .descriptor(containerCreatorDescriptor)
                        .build()
        );
    }
}
