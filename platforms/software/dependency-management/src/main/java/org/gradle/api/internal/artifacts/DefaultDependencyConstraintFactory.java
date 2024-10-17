/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyConstraintFactoryInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.notations.DependencyConstraintNotationParser;
import org.gradle.api.model.ObjectFactory;

import javax.annotation.Nullable;


public class DefaultDependencyConstraintFactory implements DependencyConstraintFactoryInternal {
    private final ObjectFactory objectFactory;
    private final DependencyConstraintNotationParser dependencyConstraintNotationParser;
    private final AttributesFactory attributesFactory;

    public DefaultDependencyConstraintFactory(
        ObjectFactory objectFactory,
        DependencyConstraintNotationParser dependencyConstraintNotationParser,
        AttributesFactory attributesFactory
    ) {
        this.objectFactory = objectFactory;
        this.dependencyConstraintNotationParser = dependencyConstraintNotationParser;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public DependencyConstraint createDependencyConstraint(Object dependencyNotation) {
        DependencyConstraint dependencyConstraint = dependencyConstraintNotationParser.getNotationParser().parseNotation(dependencyNotation);
        injectServices(dependencyConstraint);
        return dependencyConstraint;
    }

    private void injectServices(DependencyConstraint dependency) {
        if (dependency instanceof DefaultDependencyConstraint) {
            ((DefaultDependencyConstraint) dependency).setAttributesFactory(attributesFactory);
        }
    }

    // region DependencyConstraintFactory methods

    @Override
    public DependencyConstraint create(CharSequence dependencyNotation) {
        DependencyConstraint dependencyConstraint = dependencyConstraintNotationParser.getStringNotationParser().parseNotation(dependencyNotation.toString());
        injectServices(dependencyConstraint);
        return dependencyConstraint;
    }

    @Override
    public DependencyConstraint create(@Nullable String group, String name, @Nullable String version) {
        DefaultDependencyConstraint dependencyConstraint = objectFactory.newInstance(DefaultDependencyConstraint.class, group, name, version);
        injectServices(dependencyConstraint);
        return dependencyConstraint;
    }

    @Override
    public DependencyConstraint create(MinimalExternalModuleDependency dependency) {
        DependencyConstraint dependencyConstraint = dependencyConstraintNotationParser.getMinimalExternalModuleDependencyNotationParser().parseNotation(dependency);
        injectServices(dependencyConstraint);
        return dependencyConstraint;
    }

    @Override
    public DependencyConstraint create(ProjectDependency project) {
        DependencyConstraint dependencyConstraint = dependencyConstraintNotationParser.getProjectDependencyNotationParser().parseNotation(project);
        injectServices(dependencyConstraint);
        return dependencyConstraint;
    }

    // endregion
}
