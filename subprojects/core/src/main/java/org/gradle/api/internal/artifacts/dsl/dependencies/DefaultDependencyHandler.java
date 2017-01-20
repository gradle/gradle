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
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformRegistrations;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityRuleChain;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;

import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ArtifactAttributes.*;

public class DefaultDependencyHandler extends GroovyObjectSupport implements DependencyHandler, MethodMixIn {
    private static final Action<AttributeMatchingStrategy<String>> ARTIFACT_ATTRIBUTE_CONFIG = new Action<AttributeMatchingStrategy<String>>() {
        @Override
        public void execute(AttributeMatchingStrategy<String> stringAttributeMatchingStrategy) {
            CompatibilityRuleChain<String> compatibilityRules = stringAttributeMatchingStrategy.getCompatibilityRules();
            compatibilityRules.assumeCompatibleWhenMissing();
        }
    };
    private static final Action<AttributesSchema> CONFIGURE_DEFAULT_SCHEMA_ACTION = new Action<AttributesSchema>() {
        @Override
        public void execute(AttributesSchema attributesSchema) {
            attributesSchema.attribute(ARTIFACT_FORMAT, ARTIFACT_ATTRIBUTE_CONFIG);
            attributesSchema.attribute(ARTIFACT_CLASSIFIER, ARTIFACT_ATTRIBUTE_CONFIG);
            attributesSchema.attribute(ARTIFACT_EXTENSION, ARTIFACT_ATTRIBUTE_CONFIG);
        }
    };

    private final ConfigurationContainer configurationContainer;
    private final DependencyFactory dependencyFactory;
    private final ProjectFinder projectFinder;
    private final ComponentMetadataHandler componentMetadataHandler;
    private final ComponentModuleMetadataHandler componentModuleMetadataHandler;
    private final ArtifactResolutionQueryFactory resolutionQueryFactory;
    private final AttributesSchema attributesSchema;
    private final ArtifactTransformRegistrations transforms;
    private final DynamicMethods dynamicMethods;

    public DefaultDependencyHandler(ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory,
                                    ProjectFinder projectFinder, ComponentMetadataHandler componentMetadataHandler, ComponentModuleMetadataHandler componentModuleMetadataHandler,
                                    ArtifactResolutionQueryFactory resolutionQueryFactory, AttributesSchema attributesSchema,
                                    ArtifactTransformRegistrations transforms) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.projectFinder = projectFinder;
        this.componentMetadataHandler = componentMetadataHandler;
        this.componentModuleMetadataHandler = componentModuleMetadataHandler;
        this.resolutionQueryFactory = resolutionQueryFactory;
        this.attributesSchema = attributesSchema;
        this.transforms = transforms;
        configureSchema();
        dynamicMethods = new DynamicMethods();
    }

    @Override
    public Dependency add(String configurationName, Object dependencyNotation) {
        return add(configurationName, dependencyNotation, null);
    }

    @Override
    public Dependency add(String configurationName, Object dependencyNotation, Closure configureClosure) {
        return doAdd(configurationContainer.findByName(configurationName), dependencyNotation, configureClosure);
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

    public void components(Action<? super ComponentMetadataHandler> configureAction) {
        configureAction.execute(getComponents());
    }

    public ComponentMetadataHandler getComponents() {
        return componentMetadataHandler;
    }

    public void modules(Action<? super ComponentModuleMetadataHandler> configureAction) {
        configureAction.execute(getModules());
    }

    public ComponentModuleMetadataHandler getModules() {
        return componentModuleMetadataHandler;
    }

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
        attributesSchema(CONFIGURE_DEFAULT_SCHEMA_ACTION);
    }

    public void registerTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
        transforms.registerTransform(type, config);
    }

    private class DynamicMethods implements MethodAccess {
        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return arguments.length != 0 && configurationContainer.findByName(name) != null;
        }

        @Override
        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            if (arguments.length == 0) {
                return;
            }
            Configuration configuration = configurationContainer.findByName(name);
            if (configuration == null) {
                return;
            }
            List<?> normalizedArgs = CollectionUtils.flattenCollections(arguments);
            if (normalizedArgs.size() == 2 && normalizedArgs.get(1) instanceof Closure) {
                result.result(doAdd(configuration, normalizedArgs.get(0), (Closure) normalizedArgs.get(1)));
            } else if (normalizedArgs.size() == 1) {
                result.result(doAdd(configuration, normalizedArgs.get(0), null));
            } else {
                for (Object arg : normalizedArgs) {
                    doAdd(configuration, arg, null);
                }
                result.result(null);
            }
        }
    }
}
