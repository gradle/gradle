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

import org.gradle.api.Incubating;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelMaps;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.util.DeprecationLogger;

import java.util.Collections;
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

    public static <T extends BaseComponentSpec> T create(Class<? extends ComponentSpec> publicType, Class<T> implementationType, ComponentSpecIdentifier identifier, MutableModelNode modelNode) {
        nextComponentInfo.set(new ComponentInfo(identifier, modelNode, publicType.getSimpleName()));
        try {
            try {
                return DirectInstantiator.INSTANCE.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create component of type %s", publicType.getSimpleName()), e.getCause());
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
        binaries = ModelMaps.addModelMapNode(modelNode, BinarySpec.class, "binaries");
        sources = ModelMaps.addModelMapNode(modelNode, LanguageSourceSet.class, "sources");
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
        return ModelMaps.asMutableView(sources, LanguageSourceSet.class, modelNode.toString() + ".getSources()");
    }

    @Override
    public ModelMap<BinarySpec> getBinaries() {
        return ModelMaps.asMutableView(binaries, BinarySpec.class, modelNode.toString() + ".getBinaries()");
    }

    public Set<? extends Class<? extends TransformationFileType>> getInputTypes() {
        return Collections.emptySet();
    }

    private static class ComponentInfo {
        final ComponentSpecIdentifier componentIdentifier;
        final MutableModelNode modelNode;
        final String typeName;

        private ComponentInfo(
            ComponentSpecIdentifier componentIdentifier,
            MutableModelNode modelNode,
            String typeName
        ) {
            this.componentIdentifier = componentIdentifier;
            this.modelNode = modelNode;
            this.typeName = typeName;
        }
    }

}
