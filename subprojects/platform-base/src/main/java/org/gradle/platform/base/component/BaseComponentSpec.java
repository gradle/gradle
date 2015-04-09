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
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.Collections;
import java.util.Set;

/**
 * Base class for custom component implementations.
 * A custom implementation of {@link ComponentSpec} must extend this type.
 */
@Incubating
public abstract class BaseComponentSpec implements ComponentSpecInternal {
    private static ThreadLocal<ComponentInfo> nextComponentInfo = new ThreadLocal<ComponentInfo>();
    private final FunctionalSourceSet mainSourceSet;

    private final ComponentSpecIdentifier identifier;
    private final String typeName;
    private final DomainObjectSet<BinarySpec> binaries = new DefaultDomainObjectSet<BinarySpec>(BinarySpec.class);

    public static <T extends BaseComponentSpec> T create(Class<T> type, ComponentSpecIdentifier identifier, FunctionalSourceSet mainSourceSet, Instantiator instantiator) {
        if (type.equals(BaseComponentSpec.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseComponentSpec.");
        }
        nextComponentInfo.set(new ComponentInfo(identifier, type.getSimpleName(), mainSourceSet));
        try {
            try {
                return instantiator.newInstance(type);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create component of type %s", type.getSimpleName()), e.getCause());
            }
        } finally {
            nextComponentInfo.set(null);
        }
    }

    protected BaseComponentSpec() {
        this(nextComponentInfo.get());
    }

    private BaseComponentSpec(ComponentInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseComponentSpec is not permitted. Use a ComponentTypeBuilder instead.");
        }

        this.identifier = info.componentIdentifier;
        this.typeName = info.typeName;
        this.mainSourceSet = info.sourceSets;
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

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class, mainSourceSet);
    }

    public DomainObjectSet<BinarySpec> getBinaries() {
        return binaries;
    }

    @Override
    public void binaries(Action<? super DomainObjectSet<BinarySpec>> action) {
        action.execute(binaries);
    }

    public FunctionalSourceSet getSources() {
        return mainSourceSet;
    }

    public void sources(Action<? super PolymorphicDomainObjectContainer<LanguageSourceSet>> action) {
        action.execute(mainSourceSet);
    }

    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return Collections.emptySet();
    }

    private static class ComponentInfo {
        final ComponentSpecIdentifier componentIdentifier;
        final String typeName;
        final FunctionalSourceSet sourceSets;

        private ComponentInfo(ComponentSpecIdentifier componentIdentifier,
                              String typeName,
                              FunctionalSourceSet sourceSets) {
            this.componentIdentifier = componentIdentifier;
            this.typeName = typeName;
            this.sourceSets = sourceSets;
        }
    }
}