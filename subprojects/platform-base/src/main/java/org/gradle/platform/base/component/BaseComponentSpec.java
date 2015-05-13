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

import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.DomainObjectCollectionBackedModelMap;
import org.gradle.model.internal.core.ModelMapGroovyDecorator;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryContainer;

import java.util.Collections;
import java.util.Set;

/**
 * Base class for custom component implementations. A custom implementation of {@link ComponentSpec} must extend this type.
 */
@Incubating
public abstract class BaseComponentSpec implements ComponentSpecInternal {
    private static ThreadLocal<ComponentInfo> nextComponentInfo = new ThreadLocal<ComponentInfo>();
    private final FunctionalSourceSet mainSourceSet;
    private final ModelMap<LanguageSourceSet> source;

    private final ComponentSpecIdentifier identifier;
    private final String typeName;
    private final DefaultBinaryContainer binaries;
    private final ModelMap<BinarySpec> binariesMap;

    public static <T extends BaseComponentSpec> T create(Class<T> type, ComponentSpecIdentifier identifier, FunctionalSourceSet mainSourceSet, Instantiator instantiator) {
        if (type.equals(BaseComponentSpec.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseComponentSpec.");
        }
        nextComponentInfo.set(new ComponentInfo(identifier, type.getSimpleName(), mainSourceSet, instantiator));
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
        this.source = ModelMapGroovyDecorator.alwaysMutable(
            NamedDomainObjectSetBackedModelMap.ofNamed(
                LanguageSourceSet.class,
                mainSourceSet,
                new NamedEntityInstantiator<LanguageSourceSet>() {
                    public <S extends LanguageSourceSet> S create(String name, Class<S> type) {
                        return mainSourceSet.create(name, type);
                    }
                }
            )
        );
        this.binaries = info.instantiator.newInstance(DefaultBinaryContainer.class, info.instantiator);
        this.binariesMap = ModelMapGroovyDecorator.alwaysMutable(
            NamedDomainObjectSetBackedModelMap.ofNamed(
                BinarySpec.class,
                this.binaries,
                this.binaries
            )
        );
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
        return source;
    }

    @Override
    public void sources(Action<? super ModelMap<LanguageSourceSet>> action) {
        action.execute(source);
    }

    @Override
    public ModelMap<BinarySpec> getBinaries() {
        return binariesMap;
    }

    @Override
    public ExtensiblePolymorphicDomainObjectContainer<BinarySpec> getBinariesContainer() {
        return binaries;
    }

    @Override
    public void binaries(Action<? super ModelMap<BinarySpec>> action) {
        action.execute(binariesMap);
    }

    public FunctionalSourceSet getSources() {
        return mainSourceSet;
    }

    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return Collections.emptySet();
    }

    private static class ComponentInfo {
        final ComponentSpecIdentifier componentIdentifier;
        final String typeName;
        final FunctionalSourceSet sourceSets;
        final Instantiator instantiator;

        private ComponentInfo(ComponentSpecIdentifier componentIdentifier,
                              String typeName,
                              FunctionalSourceSet sourceSets, Instantiator instantiator) {
            this.componentIdentifier = componentIdentifier;
            this.typeName = typeName;
            this.sourceSets = sourceSets;
            this.instantiator = instantiator;
        }
    }

    private static class NamedDomainObjectSetBackedModelMap<T> extends DomainObjectCollectionBackedModelMap<T, NamedDomainObjectSet<T>> {


        private NamedDomainObjectSetBackedModelMap(Class<T> elementClass, NamedDomainObjectSet<T> backingSet, NamedEntityInstantiator<T> instantiator, org.gradle.api.Namer<Object> namer) {
            super(elementClass, backingSet, instantiator, namer);
        }

        private <S> NamedDomainObjectSet<S> toNonSubtype(final Class<S> type) {
            NamedDomainObjectSet<T> matching = backingCollection.matching(new WithType<T>(type));

            return Cast.uncheckedCast(matching);
        }

        @Override
        protected <S> ModelMap<S> toNonSubtypeMap(Class<S> type) {
            NamedDomainObjectSet<S> cast = toNonSubtype(type);
            return new NamedDomainObjectSetBackedModelMap<S>(type, cast, new NamedEntityInstantiator<S>() {
                @Override
                public <D extends S> D create(String name, Class<D> type) {
                    throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type, elementClass.toString()));
                }
            }, namer);
        }

        protected <S extends T> ModelMap<S> toSubtypeMap(Class<S> itemSubtype) {
            NamedEntityInstantiator<S> instantiator = Cast.uncheckedCast(this.instantiator);
            return new NamedDomainObjectSetBackedModelMap<S>(itemSubtype, backingCollection.withType(itemSubtype), instantiator, namer);
        }

        @Nullable
        @Override
        public T get(String name) {
            return backingCollection.findByName(name);
        }

        @Override
        public Set<String> keySet() {
            return backingCollection.getNames();
        }

        @Override
        public <S> void withType(Class<S> type, Action<? super S> configAction) {
            toNonSubtype(type).all(configAction);
        }

        private static class WithType<T> implements Spec<T> {
            private final Class<?> type;

            public WithType(Class<?> type) {
                this.type = type;
            }

            @Override
            public boolean isSatisfiedBy(T element) {
                return type.isInstance(element);
            }
        }

        private static <T> NamedDomainObjectSetBackedModelMap<T> ofNamed(Class<T> elementType, NamedDomainObjectSet<T> domainObjectSet, NamedEntityInstantiator<T> instantiator) {
            return new NamedDomainObjectSetBackedModelMap<T>(
                elementType,
                domainObjectSet,
                instantiator,
                new org.gradle.api.Namer<Object>() {
                    public String determineName(Object object) {
                        return Cast.cast(Named.class, object).getName();
                    }
                }
            );
        }
    }
}
