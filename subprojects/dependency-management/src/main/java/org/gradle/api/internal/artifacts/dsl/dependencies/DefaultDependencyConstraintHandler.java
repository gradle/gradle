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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;

public class DefaultDependencyConstraintHandler implements DependencyConstraintHandler, MethodMixIn {
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactory dependencyFactory;
    private final DynamicAddDependencyMethods dynamicMethods;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final PlatformSupport platformSupport;
    private final Category platform;
    private final Category enforcedPlatform;

    public DefaultDependencyConstraintHandler(ConfigurationContainer configurationContainer,
                                              DependencyFactory dependencyFactory,
                                              NamedObjectInstantiator namedObjectInstantiator,
                                              PlatformSupport platformSupport) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.dynamicMethods = new DynamicAddDependencyMethods(configurationContainer, new DependencyConstraintAdder());
        this.namedObjectInstantiator = namedObjectInstantiator;
        this.platformSupport = platformSupport;
        platform = toCategory(Category.REGULAR_PLATFORM);
        enforcedPlatform = toCategory(Category.ENFORCED_PLATFORM);
    }

    @Override
    public DependencyConstraint add(String configurationName, Object dependencyNotation) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, null);
    }

    @Override
    public DependencyConstraint add(String configurationName, Object dependencyNotation, Action<? super DependencyConstraint> configureAction) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureAction);
    }

    @Override
    public DependencyConstraint create(Object dependencyNotation) {
        return doCreate(dependencyNotation, null);
    }

    @Override
    public DependencyConstraint create(Object dependencyNotation, Action<? super DependencyConstraint> configureAction) {
        return doCreate(dependencyNotation, configureAction);
    }

    @Override
    public DependencyConstraint enforcedPlatform(Object notation) {
        DependencyConstraintInternal platformDependency = (DependencyConstraintInternal) create(notation);
        platformDependency.setForce(true);
        platformSupport.addPlatformAttribute(platformDependency, enforcedPlatform);
        return platformDependency;
    }

    @Override
    public DependencyConstraint enforcedPlatform(Object notation, Action<? super DependencyConstraint> configureAction) {
        DependencyConstraint dep = enforcedPlatform(notation);
        configureAction.execute(dep);
        return dep;
    }

    private DependencyConstraint doCreate(Object dependencyNotation, @Nullable Action<? super DependencyConstraint> configureAction) {
        DependencyConstraint dependencyConstraint = dependencyFactory.createDependencyConstraint(dependencyNotation);
        if (configureAction != null) {
            configureAction.execute(dependencyConstraint);
        }
        return dependencyConstraint;
    }

    private DependencyConstraint doAdd(Configuration configuration, Object dependencyNotation, @Nullable Action<? super DependencyConstraint> configureAction) {
        DependencyConstraint dependency = doCreate(dependencyNotation, configureAction);
        configuration.getDependencyConstraints().add(dependency);
        return dependency;
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    private Category toCategory(String category) {
        return namedObjectInstantiator.named(Category.class, category);
    }

    private class DependencyConstraintAdder implements DynamicAddDependencyMethods.DependencyAdder<DependencyConstraint> {
        @Override
        @SuppressWarnings("rawtypes")
        public DependencyConstraint add(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
            DependencyConstraint dependencyConstraint = ConfigureUtil.configure(configureClosure, dependencyFactory.createDependencyConstraint(dependencyNotation));
            configuration.getDependencyConstraints().add(dependencyConstraint);
            return dependencyConstraint;
        }
    }
}
