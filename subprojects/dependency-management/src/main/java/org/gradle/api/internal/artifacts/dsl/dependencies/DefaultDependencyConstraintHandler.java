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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;

public class DefaultDependencyConstraintHandler implements DependencyConstraintHandler, MethodMixIn {
    private final static DependencyConstraint DUMMY_CONSTRAINT = new DependencyConstraintInternal() {
        private InvalidUserCodeException shouldNotBeCalled() {
            return new InvalidUserCodeException("You shouldn't use a dependency constraint created via a Provider directly");
        }

        @Override
        public void setForce(boolean force) {
            throw shouldNotBeCalled();
        }

        @Override
        public boolean isForce() {
            throw shouldNotBeCalled();
        }

        @Override
        public void version(Action<? super MutableVersionConstraint> configureAction) {
            throw shouldNotBeCalled();
        }

        @Override
        public String getReason() {
            throw shouldNotBeCalled();
        }

        @Override
        public void because(@Nullable String reason) {
            throw shouldNotBeCalled();
        }

        @Override
        public AttributeContainer getAttributes() {
            throw shouldNotBeCalled();
        }

        @Override
        public DependencyConstraint attributes(Action<? super AttributeContainer> configureAction) {
            throw shouldNotBeCalled();
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            throw shouldNotBeCalled();
        }

        @Override
        public String getGroup() {
            throw shouldNotBeCalled();
        }

        @Override
        public String getName() {
            throw shouldNotBeCalled();
        }

        @Nullable
        @Override
        public String getVersion() {
            throw shouldNotBeCalled();
        }

        @Override
        public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
            throw shouldNotBeCalled();
        }

        @Override
        public ModuleIdentifier getModule() {
            throw shouldNotBeCalled();
        }

        @Override
        public DependencyConstraint copy() {
            throw shouldNotBeCalled();
        }
    };
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactoryInternal dependencyFactory;
    private final DynamicAddDependencyMethods dynamicMethods;
    private final ObjectFactory objects;
    private final PlatformSupport platformSupport;
    private final Category platform;
    private final Category enforcedPlatform;

    public DefaultDependencyConstraintHandler(ConfigurationContainer configurationContainer,
                                              DependencyFactoryInternal dependencyFactory,
                                              ObjectFactory objects,
                                              PlatformSupport platformSupport) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.dynamicMethods = new DynamicAddDependencyMethods(configurationContainer, new DependencyConstraintAdder());
        this.objects = objects;
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
        if(dependencyNotation instanceof ProviderConvertible<?>) {
            return doAddProvider(configuration, ((ProviderConvertible<?>) dependencyNotation).asProvider(), configureAction);
        }
        if (dependencyNotation instanceof Provider<?>) {
            return doAddProvider(configuration, (Provider<?>) dependencyNotation, configureAction);
        }
        DependencyConstraint dependency = doCreate(dependencyNotation, configureAction);
        configuration.getDependencyConstraints().add(dependency);
        return dependency;
    }

    private DependencyConstraint doAddProvider(Configuration configuration, Provider<?> dependencyNotation, @Nullable Action<? super DependencyConstraint> configureAction) {
        if (dependencyNotation instanceof ProviderInternal<?>) {
            ProviderInternal<?> provider = (ProviderInternal<?>) dependencyNotation;
            if (provider.getType() != null && ExternalModuleDependencyBundle.class.isAssignableFrom(provider.getType())) {
                ExternalModuleDependencyBundle bundle = Cast.uncheckedCast(provider.get());
                for (MinimalExternalModuleDependency dependency : bundle) {
                    doAdd(configuration, dependency, configureAction);
                }
                return DUMMY_CONSTRAINT;
            }
        }
        Provider<DependencyConstraint> lazyConstraint = dependencyNotation.map(mapDependencyConstraintProvider(configureAction));
        configuration.getDependencyConstraints().addLater(lazyConstraint);
        // Return a fake dependency constraint object to satisfy Kotlin DSL backwards compatibility
        return DUMMY_CONSTRAINT;
    }

    private <T> Transformer<DependencyConstraint, T> mapDependencyConstraintProvider(@Nullable Action<? super DependencyConstraint> configurationAction) {
        return lazyNotation -> doCreate(lazyNotation, configurationAction);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    private Category toCategory(String category) {
        return objects.named(Category.class, category);
    }

    private class DependencyConstraintAdder implements DynamicAddDependencyMethods.DependencyAdder<DependencyConstraint> {
        @Override
        @SuppressWarnings("rawtypes")
        public DependencyConstraint add(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
            if(dependencyNotation instanceof ProviderConvertible<?>) {
                return doAddProvider(configuration, ((ProviderConvertible<?>) dependencyNotation).asProvider(), ConfigureUtil.configureUsing(configureClosure));
            }
            if (dependencyNotation instanceof Provider<?>) {
                return doAddProvider(configuration, (Provider<?>) dependencyNotation, ConfigureUtil.configureUsing(configureClosure));
            }
            DependencyConstraint dependencyConstraint = ConfigureUtil.configure(configureClosure, dependencyFactory.createDependencyConstraint(dependencyNotation));
            configuration.getDependencyConstraints().add(dependencyConstraint);
            return dependencyConstraint;
        }
    }
}
