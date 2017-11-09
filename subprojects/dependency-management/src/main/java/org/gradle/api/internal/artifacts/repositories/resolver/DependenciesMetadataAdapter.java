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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.model.GradleDependencyMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;

public class DependenciesMetadataAdapter extends AbstractList<DependencyMetadata> implements DependenciesMetadata {
    private final List<org.gradle.internal.component.model.DependencyMetadata> dependenciesMetadata;
    private final Map<Integer, DependencyMetadata> dependencyMetadataAdapters;
    private final Instantiator instantiator;
    private final NotationParser<Object, DependencyMetadata> dependencyNotationParser;

    public DependenciesMetadataAdapter(List<org.gradle.internal.component.model.DependencyMetadata> dependenciesMetadata, Instantiator instantiator, NotationParser<Object, DependencyMetadata> dependencyNotationParser) {
        this.dependenciesMetadata = dependenciesMetadata;
        this.dependencyMetadataAdapters = Maps.newHashMap();
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
    }

    @Override
    public DependencyMetadata get(int index) {
        if (!dependencyMetadataAdapters.containsKey(index)) {
            dependencyMetadataAdapters.put(index, instantiator.newInstance(DependencyMetadataAdapter.class, dependenciesMetadata, index));
        }
        return dependencyMetadataAdapters.get(index);
    }

    @Override
    public int size() {
        return dependenciesMetadata.size();
    }

    @Override
    public DependencyMetadata remove(int index) {
        DependencyMetadata componentDependencyMetadata = get(index);
        dependenciesMetadata.remove(index);
        dependencyMetadataAdapters.remove(index);
        return componentDependencyMetadata;
    }

    @Override
    public void add(String dependencyNotation) {
        doAdd(dependencyNotation, null);
    }

    @Override
    public void add(Map<String, String> dependencyNotation) {
        doAdd(dependencyNotation, null);
    }

    @Override
    public void add(String dependencyNotation, Action<DependencyMetadata> configureAction) {
        doAdd(dependencyNotation, configureAction);
    }

    @Override
    public void add(Map<String, String> dependencyNotation, Action<DependencyMetadata> configureAction) {
        doAdd(dependencyNotation, configureAction);
    }

    private void doAdd(Object dependencyNotation, @Nullable Action<DependencyMetadata> configureAction) {
        DependencyMetadata dependencyMetadata = dependencyNotationParser.parseNotation(dependencyNotation);
        if (configureAction != null) {
            configureAction.execute(dependencyMetadata);
        }
        dependenciesMetadata.add(toDependencyMetadata(dependencyMetadata));
    }

    private org.gradle.internal.component.model.DependencyMetadata toDependencyMetadata(DependencyMetadata details) {
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(details.getGroup(), details.getName(), DefaultImmutableVersionConstraint.of(details.getVersion()));
        return new GradleDependencyMetadata(selector);
    }
}
