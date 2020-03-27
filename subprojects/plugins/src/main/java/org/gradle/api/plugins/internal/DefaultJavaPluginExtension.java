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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.jpms.ModularClasspathHandling;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.jpms.DefaultModularClasspathHandling;

import java.util.regex.Pattern;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;
import static org.gradle.api.plugins.JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.SOURCES_ELEMENTS_CONFIGURATION_NAME;
import static org.gradle.api.plugins.internal.JvmPluginsHelper.configureDocumentationVariantWithArtifact;
import static org.gradle.api.plugins.internal.JvmPluginsHelper.findJavaComponent;

public class DefaultJavaPluginExtension implements JavaPluginExtension {
    private final static Pattern VALID_FEATURE_NAME = Pattern.compile("[a-zA-Z0-9]+");

    private final JavaPluginConvention convention;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final SoftwareComponentContainer components;
    private final TaskContainer tasks;
    private final Project project;
    private final ModularClasspathHandling modularClasspathHandling;
    private final Property<Integer> release;

    public DefaultJavaPluginExtension(JavaPluginConvention convention,
                                      Project project) {
        this.convention = convention;
        this.configurations = project.getConfigurations();
        this.objectFactory = project.getObjects();
        this.components = project.getComponents();
        this.tasks = project.getTasks();
        this.project = project;
        this.modularClasspathHandling = project.getObjects().newInstance(DefaultModularClasspathHandling.class);
        this.release = project.getObjects().property(Integer.class);
    }

    @Override
    public Property<Integer> getRelease() {
        return release;
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
        Capability defaultCapability = new ProjectDerivedCapability(project, name);
        DefaultJavaFeatureSpec spec = new DefaultJavaFeatureSpec(
                validateFeatureName(name),
                defaultCapability, convention, this,
                configurations,
                objectFactory,
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
    public void withJavadocJar() {
        TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        SourceSet main = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        configureDocumentationVariantWithArtifact(JAVADOC_ELEMENTS_CONFIGURATION_NAME, null, JAVADOC, ImmutableList.of(), main.getJavadocJarTaskName(), tasks.named(main.getJavadocTaskName()), findJavaComponent(components), configurations, tasks, objectFactory);
    }

    @Override
    public void withSourcesJar() {
        TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        SourceSet main = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        configureDocumentationVariantWithArtifact(SOURCES_ELEMENTS_CONFIGURATION_NAME, null, SOURCES, ImmutableList.of(), main.getSourcesJarTaskName(), main.getAllSource(), findJavaComponent(components), configurations, tasks, objectFactory);
    }

    @Override
    public ModularClasspathHandling getModularClasspathHandling() {
        return modularClasspathHandling;
    }

    private static String validateFeatureName(String name) {
        if (!VALID_FEATURE_NAME.matcher(name).matches()) {
            throw new InvalidUserDataException("Invalid feature name '" + name + "'. Must match " + VALID_FEATURE_NAME.pattern());
        }
        return name;
    }
}
