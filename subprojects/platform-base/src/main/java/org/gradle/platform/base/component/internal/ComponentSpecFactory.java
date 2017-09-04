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

package org.gradle.platform.base.component.internal;

import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.typeregistration.BaseInstanceFactory;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;

import javax.annotation.Nullable;

public class ComponentSpecFactory extends BaseInstanceFactory<ComponentSpec> {
    private final ProjectIdentifier projectIdentifier;

    public ComponentSpecFactory(final ProjectIdentifier projectIdentifier, final Instantiator instantiator, final ITaskFactory taskFactory, final SourceDirectorySetFactory sourceDirectorySetFactory) {
        super(ComponentSpec.class);
        this.projectIdentifier = projectIdentifier;
        registerFactory(DefaultComponentSpec.class, new ImplementationFactory<ComponentSpec, DefaultComponentSpec>() {
            @Override
            public <T extends DefaultComponentSpec> T create(ModelType<? extends ComponentSpec> publicType, ModelType<T> implementationType, String name, MutableModelNode componentNode) {
                ComponentSpecIdentifier id = getId(findOwner(componentNode), name);
                return DefaultComponentSpec.create(publicType.getConcreteClass(), implementationType.getConcreteClass(), id, componentNode);
            }
        });
        registerFactory(BaseBinarySpec.class, new ImplementationFactory<BinarySpec, BaseBinarySpec>() {
            @Override
            public <T extends BaseBinarySpec> T create(ModelType<? extends BinarySpec> publicType, ModelType<T> implementationType, String name, MutableModelNode binaryNode) {
                MutableModelNode componentNode = findOwner(binaryNode);
                ComponentSpecIdentifier id = getId(componentNode, name);
                return BaseBinarySpec.create(
                        publicType.getConcreteClass(),
                        implementationType.getConcreteClass(),
                        id,
                        binaryNode,
                        componentNode,
                        instantiator,
                        taskFactory);
            }
        });
        registerFactory(BaseLanguageSourceSet.class, new ImplementationFactory<LanguageSourceSet, BaseLanguageSourceSet>() {
            @Override
            public <T extends BaseLanguageSourceSet> T create(ModelType<? extends LanguageSourceSet> publicType, ModelType<T> implementationType, String sourceSetName, MutableModelNode node) {
                ComponentSpecIdentifier id = getId(findOwner(node), sourceSetName);
                return Cast.uncheckedCast(BaseLanguageSourceSet.create(publicType.getConcreteClass(), implementationType.getConcreteClass(), id, sourceDirectorySetFactory));
            }
        });
    }

    @Nullable
    private ComponentSpecIdentifier getId(@Nullable MutableModelNode ownerNode, String name) {
        if (ownerNode != null) {
            ComponentSpecInternal componentSpec = ownerNode.asImmutable(ModelType.of(ComponentSpecInternal.class), null).getInstance();
            return componentSpec.getIdentifier().child(name);
        }

        return new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
    }

    @Nullable
    private MutableModelNode findOwner(MutableModelNode modelNode) {
        MutableModelNode grandparentNode = modelNode.getParent().getParent();
        if (grandparentNode != null && grandparentNode.canBeViewedAs(ModelType.of(ComponentSpecInternal.class))) {
            return grandparentNode;
        }

        return null;
    }
}
