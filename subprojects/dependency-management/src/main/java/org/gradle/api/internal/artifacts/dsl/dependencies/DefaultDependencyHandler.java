/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.transform.VariantTransform;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Factory;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT;

public abstract class DefaultDependencyHandler implements DependencyHandler, MethodMixIn {
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactory dependencyFactory;
    private final ProjectFinder projectFinder;
    private final DependencyConstraintHandler dependencyConstraintHandler;
    private final ComponentMetadataHandler componentMetadataHandler;
    private final ComponentModuleMetadataHandler componentModuleMetadataHandler;
    private final ArtifactResolutionQueryFactory resolutionQueryFactory;
    private final AttributesSchema attributesSchema;
    private final VariantTransformRegistry transforms;
    private final Factory<ArtifactTypeContainer> artifactTypeContainer;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final DynamicAddDependencyMethods dynamicMethods;

    public DefaultDependencyHandler(ConfigurationContainer configurationContainer,
                                    DependencyFactory dependencyFactory,
                                    ProjectFinder projectFinder,
                                    DependencyConstraintHandler dependencyConstraintHandler,
                                    ComponentMetadataHandler componentMetadataHandler,
                                    ComponentModuleMetadataHandler componentModuleMetadataHandler,
                                    ArtifactResolutionQueryFactory resolutionQueryFactory,
                                    AttributesSchema attributesSchema,
                                    VariantTransformRegistry transforms,
                                    Factory<ArtifactTypeContainer> artifactTypeContainer,
                                    NamedObjectInstantiator namedObjectInstantiator) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.projectFinder = projectFinder;
        this.dependencyConstraintHandler = dependencyConstraintHandler;
        this.componentMetadataHandler = componentMetadataHandler;
        this.componentModuleMetadataHandler = componentModuleMetadataHandler;
        this.resolutionQueryFactory = resolutionQueryFactory;
        this.attributesSchema = attributesSchema;
        this.transforms = transforms;
        this.artifactTypeContainer = artifactTypeContainer;
        this.namedObjectInstantiator = namedObjectInstantiator;
        configureSchema();
        dynamicMethods = new DynamicAddDependencyMethods(configurationContainer, new DirectDependencyAdder());
    }

    @Override
    public Dependency add(String configurationName, Object dependencyNotation) {
        return add(configurationName, dependencyNotation, null);
    }

    @Override
    public Dependency add(String configurationName, Object dependencyNotation, Closure configureClosure) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureClosure);
    }

    @Override
    public Dependency create(Object dependencyNotation) {
        return create(dependencyNotation, null);
    }

    @Override
    public Dependency create(Object dependencyNotation, Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(dependencyNotation);
        return ConfigureUtil.configure(configureClosure, dependency);
    }

    private Dependency doAdd(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
        if (dependencyNotation instanceof Configuration) {
            Configuration other = (Configuration) dependencyNotation;
            if (!configurationContainer.contains(other)) {
                throw new UnsupportedOperationException("Currently you can only declare dependencies on configurations from the same project.");
            }
            configuration.extendsFrom(other);
            return null;
        }

        Dependency dependency = create(dependencyNotation, configureClosure);
        configuration.getDependencies().add(dependency);
        return dependency;
    }

    @Override
    public Dependency module(Object notation) {
        return module(notation, null);
    }

    @Override
    public Dependency project(Map<String, ?> notation) {
        return dependencyFactory.createProjectDependencyFromMap(projectFinder, notation);
    }

    @Override
    public Dependency module(Object notation, Closure configureClosure) {
        return dependencyFactory.createModule(notation, configureClosure);
    }

    @Override
    public Dependency gradleApi() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.GRADLE_API);
    }

    @Override
    public Dependency gradleTestKit() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.GRADLE_TEST_KIT);
    }

    @Override
    public Dependency localGroovy() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.LOCAL_GROOVY);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    @Override
    public void constraints(Action<? super DependencyConstraintHandler> configureAction) {
        configureAction.execute(dependencyConstraintHandler);
    }

    @Override
    public DependencyConstraintHandler getConstraints() {
        return dependencyConstraintHandler;
    }

    @Override
    public void components(Action<? super ComponentMetadataHandler> configureAction) {
        configureAction.execute(getComponents());
    }

    @Override
    public ComponentMetadataHandler getComponents() {
        return componentMetadataHandler;
    }

    @Override
    public void modules(Action<? super ComponentModuleMetadataHandler> configureAction) {
        configureAction.execute(getModules());
    }

    @Override
    public ComponentModuleMetadataHandler getModules() {
        return componentModuleMetadataHandler;
    }

    @Override
    public ArtifactResolutionQuery createArtifactResolutionQuery() {
        return resolutionQueryFactory.createArtifactResolutionQuery();
    }

    @Override
    public AttributesSchema attributesSchema(Action<? super AttributesSchema> configureAction) {
        configureAction.execute(attributesSchema);
        return attributesSchema;
    }

    @Override
    public AttributesSchema getAttributesSchema() {
        return attributesSchema;
    }

    private void configureSchema() {
        attributesSchema.attribute(ARTIFACT_FORMAT);
    }

    @Override
    public ArtifactTypeContainer getArtifactTypes() {
        return artifactTypeContainer.create();
    }

    @Override
    public void artifactTypes(Action<? super ArtifactTypeContainer> configureAction) {
        configureAction.execute(getArtifactTypes());
    }

    @Override
    public void registerTransform(Action<? super VariantTransform> registrationAction) {
        transforms.registerTransform(registrationAction);
    }

    @Override
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        transforms.registerTransform(actionType, registrationAction);
    }

    @Override
    public Dependency platform(Object notation) {
        Dependency dependency = create(notation);
        if (dependency instanceof HasConfigurableAttributes) {
            PlatformSupport.addPlatformAttribute((HasConfigurableAttributes<Object>) dependency, toCategory(Category.REGULAR_PLATFORM));
        }
        return dependency;
    }

    @Override
    public Dependency platform(Object notation, Action<? super Dependency> configureAction) {
        Dependency dep = platform(notation);
        configureAction.execute(dep);
        return dep;
    }

    @Override
    public Dependency enforcedPlatform(Object notation) {
        Dependency platformDependency = create(notation);
        if (platformDependency instanceof ExternalModuleDependency) {
            ExternalModuleDependency externalModuleDependency = (ExternalModuleDependency) platformDependency;
            externalModuleDependency.setForce(true);
            PlatformSupport.addPlatformAttribute(externalModuleDependency, toCategory(Category.ENFORCED_PLATFORM));
        } else if (platformDependency instanceof HasConfigurableAttributes) {
            PlatformSupport.addPlatformAttribute((HasConfigurableAttributes<?>) platformDependency, toCategory(Category.ENFORCED_PLATFORM));
        }
        return platformDependency;
    }

    @Override
    public Dependency enforcedPlatform(Object notation, Action<? super Dependency> configureAction) {
        Dependency dep = enforcedPlatform(notation);
        configureAction.execute(dep);
        return dep;
    }

    private Category toCategory(String category) {
        return namedObjectInstantiator.named(Category.class, category);
    }

    private class DirectDependencyAdder implements DynamicAddDependencyMethods.DependencyAdder<Dependency> {

        @Override
        public Dependency add(Configuration configuration, Object dependencyNotation, @Nullable Closure configureAction) {
            return doAdd(configuration, dependencyNotation, configureAction);
        }
    }
}
