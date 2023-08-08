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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.ExternalModuleDependencyVariantSpec;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.artifacts.dsl.VariantMatchingFailureInterpreter;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependencyVariant;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ProjectTestFixtures;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE;
import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_CAPABILITY_APPENDIX;

public abstract class DefaultDependencyHandler implements DependencyHandler, MethodMixIn {
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactoryInternal dependencyFactory;
    private final ProjectFinder projectFinder;
    private final DependencyConstraintHandler dependencyConstraintHandler;
    private final ComponentMetadataHandler componentMetadataHandler;
    private final ComponentModuleMetadataHandler componentModuleMetadataHandler;
    private final ArtifactResolutionQueryFactory resolutionQueryFactory;
    private final AttributesSchema attributesSchema;
    private final VariantTransformRegistry transforms;
    private final Factory<ArtifactTypeContainer> artifactTypeContainer;
    private final ObjectFactory objects;
    private final PlatformSupport platformSupport;
    private final DynamicAddDependencyMethods dynamicMethods;
    private final List<VariantMatchingFailureInterpreter> variantMatchingFailureInterpreters;

    public DefaultDependencyHandler(ConfigurationContainer configurationContainer,
                                    DependencyFactoryInternal dependencyFactory,
                                    ProjectFinder projectFinder,
                                    DependencyConstraintHandler dependencyConstraintHandler,
                                    ComponentMetadataHandler componentMetadataHandler,
                                    ComponentModuleMetadataHandler componentModuleMetadataHandler,
                                    ArtifactResolutionQueryFactory resolutionQueryFactory,
                                    AttributesSchema attributesSchema,
                                    VariantTransformRegistry transforms,
                                    Factory<ArtifactTypeContainer> artifactTypeContainer,
                                    ObjectFactory objects,
                                    PlatformSupport platformSupport) {
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
        this.objects = objects;
        this.platformSupport = platformSupport;
        configureSchema();
        dynamicMethods = new DynamicAddDependencyMethods(configurationContainer, new DirectDependencyAdder());

        this.variantMatchingFailureInterpreters = new ArrayList<>();
    }

    @Override
    public Dependency add(String configurationName, Object dependencyNotation) {
        return add(configurationName, dependencyNotation, null);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Dependency add(String configurationName, Object dependencyNotation, @Nullable Closure configureClosure) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureClosure);
    }

    @Override
    public <T, U extends ExternalModuleDependency> void addProvider(String configurationName, Provider<T> dependencyNotation, Action<? super U> configuration) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation, closureOf(configuration));
    }

    @Override
    public <T> void addProvider(String configurationName, Provider<T> dependencyNotation) {
        addProvider(configurationName, dependencyNotation, Actions.doNothing());
    }

    @Override
    public <T, U extends ExternalModuleDependency> void addProviderConvertible(String configurationName, ProviderConvertible<T> dependencyNotation, Action<? super U> configuration) {
        addProvider(configurationName, dependencyNotation.asProvider(), configuration);
    }

    @Override
    public <T> void addProviderConvertible(String configurationName, ProviderConvertible<T> dependencyNotation) {
        addProviderConvertible(configurationName, dependencyNotation, Actions.doNothing());
    }

    @SuppressWarnings("ConstantConditions")
    private <U extends ExternalModuleDependency> Closure<Object> closureOf(Action<? super U> configuration) {
        return new Closure<Object>(this, this) {
            @Override
            public Object call() {
                configuration.execute(Cast.uncheckedCast(getDelegate()));
                return null;
            }

            @Override
            public Object call(Object arguments) {
                configuration.execute(Cast.uncheckedCast(arguments));
                return null;
            }
        };
    }

    @Override
    public Dependency create(Object dependencyNotation) {
        return create(dependencyNotation, null);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Dependency create(Object dependencyNotation, @Nullable Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(dependencyNotation);
        return ConfigureUtil.configure(configureClosure, dependency);
    }

    @SuppressWarnings("rawtypes")
    @Nullable
    private Dependency doAdd(Configuration configuration, Object dependencyNotation, @Nullable Closure configureClosure) {
        if (dependencyNotation instanceof Configuration) {
            throw new GradleException("Adding a Configuration as a dependency is no longer allowed as of Gradle 8.0.");
        } else if (dependencyNotation instanceof ProviderConvertible<?>) {
            return doAdd(configuration, ((ProviderConvertible<?>) dependencyNotation).asProvider(), configureClosure);
        } else if (dependencyNotation instanceof ProviderInternal<?>) {
            ProviderInternal<?> provider = (ProviderInternal<?>) dependencyNotation;
            if (provider.getType()!=null && ExternalModuleDependencyBundle.class.isAssignableFrom(provider.getType())) {
                ExternalModuleDependencyBundle bundle = Cast.uncheckedCast(provider.get());
                for (MinimalExternalModuleDependency dependency : bundle) {
                    doAddRegularDependency(configuration, dependency, configureClosure);
                }
                return null;
            }
            return doAddProvider(configuration, provider, configureClosure);
        } else if (dependencyNotation instanceof Provider<?>) {
            return doAddProvider(configuration, (Provider<?>) dependencyNotation, configureClosure);
        } else {
            return doAddRegularDependency(configuration, dependencyNotation, configureClosure);
        }
    }

    private Dependency doAddRegularDependency(Configuration configuration, Object dependencyNotation, Closure<?> configureClosure) {
        Dependency dependency = create(dependencyNotation, configureClosure);
        configuration.getDependencies().add(dependency);
        return dependency;
    }

    @Nullable
    private Dependency doAddProvider(Configuration configuration, Provider<?> dependencyNotation, Closure<?> configureClosure) {
        if (dependencyNotation instanceof ProviderInternal<?>) {
            ProviderInternal<?> provider = (ProviderInternal<?>) dependencyNotation;
            if (provider.getType() != null && ExternalModuleDependencyBundle.class.isAssignableFrom(provider.getType())) {
                ExternalModuleDependencyBundle bundle = Cast.uncheckedCast(provider.get());
                for (MinimalExternalModuleDependency dependency : bundle) {
                    doAddRegularDependency(configuration, dependency, configureClosure);
                }
                return null;
            }
        }
        Provider<Dependency> lazyDependency = dependencyNotation.map(mapDependencyProvider(configuration, configureClosure));
        configuration.getDependencies().addLater(lazyDependency);
        // Return null here because we don't want to prematurely realize the dependency
        return null;
    }

    private <T> Transformer<Dependency, T> mapDependencyProvider(Configuration configuration, Closure<?> configureClosure) {
        return lazyNotation -> {
            if (lazyNotation instanceof Configuration) {
                throw new InvalidUserDataException("Adding a configuration as a dependency using a provider isn't supported. You should call " + configuration.getName() + ".extendsFrom(" + ((Configuration) lazyNotation).getName() + ") instead");
            }
            return create(lazyNotation, configureClosure);
        };
    }

    @Deprecated
    @Override
    public Dependency module(Object notation) {
        return module(notation, null);
    }

    @Override
    public Dependency project(Map<String, ?> notation) {
        return dependencyFactory.createProjectDependencyFromMap(projectFinder, notation);
    }

    @Deprecated
    @Override
    @SuppressWarnings("rawtypes")
    public Dependency module(Object notation, @Nullable Closure configureClosure) {
        return dependencyFactory.createModule(notation, configureClosure);
    }

    @Override
    public Dependency gradleApi() {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_API);
    }

    @Override
    public Dependency gradleTestKit() {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT);
    }

    @Override
    public Dependency localGroovy() {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY);
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
        attributesSchema.attribute(ARTIFACT_TYPE_ATTRIBUTE);
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
    public <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        transforms.registerTransform(actionType, registrationAction);
    }

    @Override
    public Dependency platform(Object notation) {
        Dependency dependency = create(notation);
        if (dependency instanceof ModuleDependency) {
            ModuleDependency moduleDependency = (ModuleDependency) dependency;
            moduleDependency.endorseStrictVersions();
            platformSupport.addPlatformAttribute(moduleDependency, toCategory(Category.REGULAR_PLATFORM));
        } else if (dependency instanceof HasConfigurableAttributes) {
            platformSupport.addPlatformAttribute((HasConfigurableAttributes<?>) dependency, toCategory(Category.REGULAR_PLATFORM));
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
    @SuppressWarnings("deprecation")
    public Dependency enforcedPlatform(Object notation) {
        Dependency platformDependency = create(notation);
        if (platformDependency instanceof ExternalModuleDependency) {
            AbstractExternalModuleDependency externalModuleDependency = (AbstractExternalModuleDependency) platformDependency;
            MutableVersionConstraint constraint = (MutableVersionConstraint) externalModuleDependency.getVersionConstraint();
            constraint.strictly(externalModuleDependency.getVersion());
            platformSupport.addPlatformAttribute(externalModuleDependency, toCategory(Category.ENFORCED_PLATFORM));
        } else if (platformDependency instanceof HasConfigurableAttributes) {
            platformSupport.addPlatformAttribute((HasConfigurableAttributes<?>) platformDependency, toCategory(Category.ENFORCED_PLATFORM));
        }
        return platformDependency;
    }

    @Override
    public Dependency enforcedPlatform(Object notation, Action<? super Dependency> configureAction) {
        Dependency dep = enforcedPlatform(notation);
        configureAction.execute(dep);
        return dep;
    }

    @Override
    public Dependency testFixtures(Object notation) {
        Dependency testFixturesDependency = create(notation);
        if (testFixturesDependency instanceof ProjectDependency) {
            ProjectDependency projectDependency = (ProjectDependency) testFixturesDependency;
            projectDependency.capabilities(new ProjectTestFixtures(projectDependency.getDependencyProject()));
        } else if (testFixturesDependency instanceof ModuleDependency) {
            ModuleDependency moduleDependency = (ModuleDependency) testFixturesDependency;
            moduleDependency.capabilities(capabilities -> capabilities.requireCapability(new DefaultImmutableCapability(
                moduleDependency.getGroup(),
                moduleDependency.getName() + TEST_FIXTURES_CAPABILITY_APPENDIX,
                null)));
        }
        return testFixturesDependency;
    }

    @Override
    public Dependency testFixtures(Object notation, Action<? super Dependency> configureAction) {
        Dependency testFixturesDependency = testFixtures(notation);
        configureAction.execute(testFixturesDependency);
        return testFixturesDependency;
    }

    @Override
    public Provider<MinimalExternalModuleDependency> variantOf(Provider<MinimalExternalModuleDependency> dependencyProvider, Action<? super ExternalModuleDependencyVariantSpec> variantSpec) {
        return dependencyProvider.map(dep -> {
            DefaultExternalModuleDependencyVariantSpec spec = objects.newInstance(DefaultExternalModuleDependencyVariantSpec.class, objects, dep);
            variantSpec.execute(spec);
            return new DefaultMinimalDependencyVariant(dep, spec.attributesAction, spec.capabilitiesMutator, spec.classifier, spec.artifactType);
        });
    }

    /**
     * Implemented here instead as a default method of DependencyHandler like most of other methods with `Provider<MinimalExternalModuleDependency>` argument
     * since we don't want to expose enforcedPlatform on many places since we might deprecate enforcedPlatform in the future
     *
     * @param dependencyProvider the dependency provider
     */
    @Override
    public Provider<MinimalExternalModuleDependency> enforcedPlatform(Provider<MinimalExternalModuleDependency> dependencyProvider) {
        return variantOf(dependencyProvider, spec -> {
            DefaultExternalModuleDependencyVariantSpec defaultSpec = (DefaultExternalModuleDependencyVariantSpec) spec;
            defaultSpec.attributesAction = attrs -> attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.ENFORCED_PLATFORM));
        });
    }

    @Override
    public List<VariantMatchingFailureInterpreter> getMatchingFailureInterpreters() {
        return variantMatchingFailureInterpreters;
    }

    private Category toCategory(String category) {
        return objects.named(Category.class, category);
    }

    private class DirectDependencyAdder implements DynamicAddDependencyMethods.DependencyAdder<Dependency> {

        @Override
        @SuppressWarnings("rawtypes")
        public Dependency add(Configuration configuration, Object dependencyNotation, @Nullable Closure configureAction) {
            return doAdd(configuration, dependencyNotation, configureAction);
        }
    }

    public static class DefaultExternalModuleDependencyVariantSpec implements ExternalModuleDependencyVariantSpec {

        private final ObjectFactory objects;
        private final MinimalExternalModuleDependency dep;
        private Action<? super AttributeContainer> attributesAction = null;
        private Action<ModuleDependencyCapabilitiesHandler> capabilitiesMutator = null;
        private String classifier;
        private String artifactType;

        @Inject
        public DefaultExternalModuleDependencyVariantSpec(ObjectFactory objects, MinimalExternalModuleDependency dep) {
            this.objects = objects;
            this.dep = dep;
        }

        @Override
        public void platform() {
            this.attributesAction = attrs -> attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.REGULAR_PLATFORM));
        }

        @Override
        public void testFixtures() {
            this.capabilitiesMutator = capabilities -> capabilities.requireCapability(new DefaultImmutableCapability(dep.getModule().getGroup(), dep.getModule().getName() + TEST_FIXTURES_CAPABILITY_APPENDIX, null));
        }

        @Override
        public void classifier(String classifier) {
            this.classifier = classifier;
        }

        @Override
        public void artifactType(String artifactType) {
            this.artifactType = artifactType;
        }
    }

}
