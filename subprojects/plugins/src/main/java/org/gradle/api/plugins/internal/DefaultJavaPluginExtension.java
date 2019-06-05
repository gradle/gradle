/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.util.TextUtil;

import java.util.regex.Pattern;

import static org.gradle.api.plugins.internal.JavaPluginsHelper.addApiToSourceSet;

public class DefaultJavaPluginExtension implements JavaPluginExtension {
    private final static Pattern VALID_FEATURE_NAME = Pattern.compile("[a-zA-Z0-9]+");
    private final static String TEST_FIXTURE_SOURCESET_NAME = "testFixtures";
    private final static String TEST_FIXTURES_FEATURE_NAME = "testFixtures";
    private final static String TEST_FIXTURES_API = "testFixturesApi";
    private final static String TEST_FIXTURES_CAPABILITY_APPENDIX = "-test-fixtures";

    private final JavaPluginConvention convention;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final PluginManager pluginManager;
    private final SoftwareComponentContainer components;
    private final TaskContainer tasks;
    private final Project project;

    public DefaultJavaPluginExtension(JavaPluginConvention convention,
                                      Project project) {
        this.convention = convention;
        this.configurations = project.getConfigurations();
        this.objectFactory = project.getObjects();
        this.pluginManager = project.getPluginManager();
        this.components = project.getComponents();
        this.tasks = project.getTasks();
        this.project = project;
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        return convention.getSourceCompatibility();
    }

    @Override
    public void setSourceCompatibility(JavaVersion value) {
        convention.setSourceCompatibility(value);
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        return convention.getTargetCompatibility();
    }

    @Override
    public void setTargetCompatibility(JavaVersion value) {
        convention.setTargetCompatibility(value);
    }

    @Override
    public void registerFeature(String name, Action<? super FeatureSpec> configureAction) {
        Capability defaultCapability = new LazyDefaultFeatureCapability(project, name);
        DefaultJavaFeatureSpec spec = new DefaultJavaFeatureSpec(
                validateFeatureName(name),
                defaultCapability, convention,
                configurations,
                objectFactory,
                pluginManager,
                components,
                tasks);
        configureAction.execute(spec);
        spec.create();
    }

    @Override
    public void disableAutoTargetJvm() {
        convention.disableAutoTargetJvm();
    }

    @Override
    public void enableTestFixtures() {
        SourceSet testFixtures = convention.getSourceSets().create(TEST_FIXTURE_SOURCESET_NAME);
        registerFeature(TEST_FIXTURES_FEATURE_NAME, featureSpec -> featureSpec.usingSourceSet(testFixtures));
        addApiToSourceSet(project, testFixtures, configurations);
        createImplicitTestFixturesDependencies();
    }

    private void createImplicitTestFixturesDependencies() {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(TEST_FIXTURES_API, dependencies.create(project));
        ProjectDependency testDependency = (ProjectDependency) dependencies.add(findTestSourceSet().getImplementationConfigurationName(), dependencies.create(project));
        testDependency.capabilities(new ProjectTestFixtures(project));
    }

    private SourceSet findTestSourceSet() {
        return convention.getSourceSets().getByName("test");
    }

    @Override
    public void usesTestFixturesOf(Object notation) {
        DependencyHandler dependencies = project.getDependencies();
        Dependency testFixturesDependency = dependencies.add(findTestSourceSet().getImplementationConfigurationName(), dependencies.create(notation));
        if (testFixturesDependency instanceof ProjectDependency) {
            ProjectDependency projectDependency = (ProjectDependency) testFixturesDependency;
            projectDependency.capabilities(new ProjectTestFixtures(projectDependency.getDependencyProject()));
        } else if (testFixturesDependency instanceof ModuleDependency) {
            ModuleDependency moduleDependency = (ModuleDependency) testFixturesDependency;
            moduleDependency.capabilities(capabilities -> {
                    capabilities.requireCapability(new ImmutableCapability(
                        moduleDependency.getGroup(),
                        moduleDependency.getName() + TEST_FIXTURES_CAPABILITY_APPENDIX,
                        null));
                });
        }
    }

    private static String validateFeatureName(String name) {
        if (!VALID_FEATURE_NAME.matcher(name).matches()) {
            throw new InvalidUserDataException("Invalid feature name '" + name + "'. Must match " + VALID_FEATURE_NAME.pattern());
        }
        return name;
    }

    private static String notNull(String id, Object o) {
        if (o == null) {
            throw new InvalidUserDataException(id + " must not be null");
        }
        return o.toString();
    }

    private static class LazyDefaultFeatureCapability implements Capability {
        private final Project project;
        private final String featureName;

        private LazyDefaultFeatureCapability(Project project, String featureName) {
            this.project = project;
            this.featureName = featureName;
        }

        @Override
        public String getGroup() {
            return notNull("group", project.getGroup());
        }

        @Override
        public String getName() {
            return notNull("name", project.getName()) + "-" + TextUtil.camelToKebabCase(featureName);
        }

        @Override
        public String getVersion() {
            return notNull("version", project.getVersion());
        }
    }

    private static class ProjectTestFixtures implements Action<ModuleDependencyCapabilitiesHandler> {
        private final Project project;

        private ProjectTestFixtures(Project project) {
            this.project = project;
        }

        @Override
        public void execute(ModuleDependencyCapabilitiesHandler capabilities) {
            capabilities.requireCapability(new LazyDefaultFeatureCapability(project, TEST_FIXTURES_FEATURE_NAME));
        }
    }
}
