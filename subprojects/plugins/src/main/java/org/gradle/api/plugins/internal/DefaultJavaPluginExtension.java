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
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.TextUtil;

import java.util.regex.Pattern;

public class DefaultJavaPluginExtension implements JavaPluginExtension {
    private final static Pattern VALID_FEATURE_NAME = Pattern.compile("[a-zA-Z0-9]+");
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
}
