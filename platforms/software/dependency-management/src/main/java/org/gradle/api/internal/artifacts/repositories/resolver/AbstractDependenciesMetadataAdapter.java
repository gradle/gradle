/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public abstract class AbstractDependenciesMetadataAdapter<T extends DependencyMetadata<T>, E extends T> extends ArrayList<T> implements DependenciesMetadata<T> {
    private final Instantiator instantiator;
    private final NotationParser<Object, T> dependencyNotationParser;
    private final AttributesFactory attributesFactory;

    public AbstractDependenciesMetadataAdapter(AttributesFactory attributesFactory, Instantiator instantiator, NotationParser<Object, T> dependencyNotationParser) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
    }

    protected abstract Class<E> adapterImplementationType();

    protected abstract ModuleDependencyMetadata getAdapterMetadata(E adapter);

    protected abstract boolean isConstraint();

    protected abstract boolean isEndorsingStrictVersions(T details);

    @Override
    public void add(String dependencyNotation) {
        doAdd(dependencyNotation, null);
    }

    @Override
    public void add(Map<String, String> dependencyNotation) {
        doAdd(dependencyNotation, null);
    }

    @Override
    public void add(String dependencyNotation, Action<? super T> configureAction) {
        doAdd(dependencyNotation, configureAction);
    }

    @Override
    public void add(Map<String, String> dependencyNotation, Action<? super T> configureAction) {
        doAdd(dependencyNotation, configureAction);
    }

    private void doAdd(Object dependencyNotation, @Nullable Action<? super T> configureAction) {
        T dependencyMetadata = dependencyNotationParser.parseNotation(dependencyNotation);
        if (dependencyMetadata instanceof AbstractDependencyImpl) {
            // This is not super nice, but dependencies are created through reflection, for decoration
            // and assume a constructor with 3 arguments (Group, Name, Version) which is suitable for
            // most cases. We could create an empty attribute set directly in the AbstractDependencyImpl,
            // but then it wouldn't be mutable. Therefore we proceed with "late injection" of the attributes
            ((AbstractDependencyImpl<?>) dependencyMetadata).setAttributes(attributesFactory.mutable());
        }

        T adapted = adapt(dependencyMetadata);
        if (configureAction != null) {
            configureAction.execute(adapted);
        }
        add(adapted);
    }

    public ImmutableList<ModuleDependencyMetadata> getMetadatas() {
        return this.stream().map(this::maybeAdapt).map(this::getAdapterMetadata).collect(ImmutableList.toImmutableList());
    }

    private E maybeAdapt(T details) {
        if (adapterImplementationType().isInstance(details)) {
            return adapterImplementationType().cast(details);
        }

        return adapt(details);
    }

    private E adapt(T details) {
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(details.getModule(), DefaultImmutableVersionConstraint.of(details.getVersionConstraint()), details.getAttributes(), ImmutableSet.of());
        GradleDependencyMetadata dependencyMetadata = new GradleDependencyMetadata(selector, Collections.emptyList(), isConstraint(), isEndorsingStrictVersions(details), details.getReason(), false, null);
        return instantiator.newInstance(adapterImplementationType(), attributesFactory, dependencyMetadata);
    }
}
