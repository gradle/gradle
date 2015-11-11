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

package org.gradle.platform.base.component;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.util.DeprecationLogger;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base class for custom component implementations. A custom implementation of {@link ComponentSpec} must extend this type.
 */
@Incubating
// Needs to be here instead of the specific methods, because Java 6 and 7 will throw warnings otherwise
@SuppressWarnings("deprecation")
public class BaseComponentSpec implements ComponentSpecInternal {

    private static ThreadLocal<ComponentInfo> nextComponentInfo = new ThreadLocal<ComponentInfo>();
    private final ComponentSpecIdentifier identifier;
    private final String typeName;

    private final MutableModelNode binaries;
    private final MutableModelNode sources;
    private final MutableModelNode modelNode;

    public static <T extends BaseComponentSpec> T create(Class<T> implementationType, ComponentSpecIdentifier identifier, MutableModelNode modelNode, Instantiator instantiator) {
        nextComponentInfo.set(new ComponentInfo(identifier, modelNode, implementationType.getSimpleName(), instantiator));
        try {
            try {
                return instantiator.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create component of type %s", implementationType.getSimpleName()), e.getCause());
            }
        } finally {
            nextComponentInfo.set(null);
        }
    }

    public BaseComponentSpec() {
        this(nextComponentInfo.get());
    }

    private BaseComponentSpec(ComponentInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseComponentSpec is not permitted. Use a ComponentTypeBuilder instead.");
        }

        this.identifier = info.componentIdentifier;
        this.typeName = info.typeName;

        modelNode = info.modelNode;
        modelNode.addLink(
            ModelRegistrations.of(
                modelNode.getPath().child("binaries"), ModelReference.of(NodeInitializerRegistry.class), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode node, List<ModelView<?>> modelViews) {
                        NodeInitializerRegistry nodeInitializerRegistry = (NodeInitializerRegistry) modelViews.get(0).getInstance();
                        ChildNodeInitializerStrategy<BinarySpec> childFactory = NodeBackedModelMap.createUsingRegistry(ModelType.of(BinarySpec.class), nodeInitializerRegistry);
                        node.setPrivateData(ModelType.of(ChildNodeInitializerStrategy.class), childFactory);
                    }
                })
                .descriptor(modelNode.getDescriptor(), ".binaries")
                .withProjection(
                    ModelMapModelProjection.unmanaged(
                        BinarySpec.class,
                        ChildNodeInitializerStrategyAccessors.fromPrivateData()
                    )
                )
                .build()
        );
        binaries = modelNode.getLink("binaries");
        assert binaries != null;

        modelNode.addLink(
            ModelRegistrations.of(
                modelNode.getPath().child("sources"), ModelReference.of(NodeInitializerRegistry.class), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode node, List<ModelView<?>> modelViews) {
                        NodeInitializerRegistry nodeInitializerRegistry = (NodeInitializerRegistry) modelViews.get(0).getInstance();
                        ChildNodeInitializerStrategy<LanguageSourceSet> childFactory = NodeBackedModelMap.createUsingRegistry(ModelType.of(LanguageSourceSet.class), nodeInitializerRegistry);
                        node.setPrivateData(ModelType.of(ChildNodeInitializerStrategy.class), childFactory);
                    }
                })
                .descriptor(modelNode.getDescriptor(), ".sources")
                .withProjection(
                    ModelMapModelProjection.unmanaged(
                        LanguageSourceSet.class,
                        ChildNodeInitializerStrategyAccessors.fromPrivateData()
                    )
                )
                .build()
        );
        sources = modelNode.getLink("sources");
        assert sources != null;
    }

    public String getName() {
        return identifier.getName();
    }

    public String getProjectPath() {
        return identifier.getProjectPath();
    }

    protected String getTypeName() {
        return typeName;
    }

    public String getDisplayName() {
        return String.format("%s '%s'", getTypeName(), getName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public ModelMap<LanguageSourceSet> getSource() {
        DeprecationLogger.nagUserOfReplacedProperty("source", "sources");
        return getSources();
    }

    @Override
    public ModelMap<LanguageSourceSet> getSources() {
        sources.ensureUsable();
        return sources.asMutable(
            ModelTypes.modelMap(LanguageSourceSetInternal.PUBLIC_MODEL_TYPE),
            RuleContext.nest(modelNode.toString() + ".getSources()"),
            Collections.<ModelView<?>>emptyList()
        ).getInstance();
    }

    @Override
    public void sources(Action<? super ModelMap<LanguageSourceSet>> action) {
        action.execute(getSources());
    }

    @Override
    public ModelMap<BinarySpec> getBinaries() {
        binaries.ensureUsable();
        return binaries.asMutable(
            ModelTypes.modelMap(BinarySpecInternal.PUBLIC_MODEL_TYPE),
            RuleContext.nest(modelNode.toString() + ".getBinaries()"),
            Collections.<ModelView<?>>emptyList()
        ).getInstance();
    }

    @Override
    public void binaries(Action<? super ModelMap<BinarySpec>> action) {
        action.execute(getBinaries());
    }

    public Set<? extends Class<? extends TransformationFileType>> getInputTypes() {
        return Collections.emptySet();
    }

    private static class ComponentInfo {
        final ComponentSpecIdentifier componentIdentifier;
        final MutableModelNode modelNode;
        final String typeName;
        final Instantiator instantiator;

        private ComponentInfo(
            ComponentSpecIdentifier componentIdentifier,
            MutableModelNode modelNode,
            String typeName,
            Instantiator instantiator
        ) {
            this.componentIdentifier = componentIdentifier;
            this.modelNode = modelNode;
            this.typeName = typeName;
            this.instantiator = instantiator;
        }
    }

}
