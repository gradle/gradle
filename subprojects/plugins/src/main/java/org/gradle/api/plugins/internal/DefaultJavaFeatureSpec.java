/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.jvm.component.internal.DefaultJvmFeature;
import org.gradle.jvm.component.JvmFeature;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.jvm.component.JvmSoftwareComponent;
import org.gradle.jvm.component.SingleTargetJvmFeature;
import org.gradle.jvm.component.internal.DefaultSingleTargetJvmFeature;

import java.util.ArrayList;
import java.util.List;

public class DefaultJavaFeatureSpec implements FeatureSpecInternal {
    private final String name;
    private final List<Capability> capabilities = new ArrayList<>(1);
    private final ProjectInternal project;

    private SourceSet sourceSet;
    private boolean withJavadocJar = false;
    private boolean withSourcesJar = false;
    private boolean allowPublication = true;

    public DefaultJavaFeatureSpec(String name, ProjectInternal project) {
        this.name = name;
        this.project = project;
    }

    @Override
    public void usingSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Override
    public void capability(String group, String name, String version) {
        capabilities.add(new DefaultImmutableCapability(group, name, version));
    }

    @Override
    public void create() {
        setupConfigurations(sourceSet);
    }

    @Override
    public void withJavadocJar() {
        withJavadocJar = true;
    }

    @Override
    public void withSourcesJar() {
        withSourcesJar = true;
    }

    @Override
    public void disablePublication() {
        allowPublication = false;
    }

    private void setupConfigurations(SourceSet sourceSet) {
        if (sourceSet == null) {
            throw new InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'");
        }

        if (capabilities.isEmpty()) {
            capabilities.add(new ProjectDerivedCapability(project, name));
        }

        @SuppressWarnings("deprecation")
        ConfigurationRole role = ConfigurationRolesForMigration.INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE;
        JvmFeature feature = new DefaultJvmFeature(
            name, sourceSet, capabilities, "The '" + name + "' feature",
            project, role, SourceSet.isMain(sourceSet)
        );
        feature.withApi();

        if (withJavadocJar) {
            feature.withJavadocJar();
        }
        if (withSourcesJar) {
            feature.withSourcesJar();
        }

        SoftwareComponent component = project.getComponents().findByName("java");
        if (component instanceof JvmSoftwareComponent) {
            if (!allowPublication) {
                feature.getVariants().getByName(feature.getApiElementsConfiguration().getName()).getVisibility().isPublished().convention(false);
                feature.getVariants().getByName(feature.getRuntimeElementsConfiguration().getName()).getVisibility().isPublished().convention(false);
            }
            ((JvmSoftwareComponent) component).getFeatures().add(feature);
        } else if (component instanceof AdhocComponentWithVariants) {
            addFeatureToAdhocComponent(feature, (AdhocComponentWithVariants) component);
        }
    }

    private void addFeatureToAdhocComponent(SingleTargetJvmFeature feature, AdhocComponentWithVariants component) {
        Configuration javadocElements = feature.getJavadocElementsConfiguration();
        if (javadocElements != null) {
            component.addVariantsFromConfiguration(javadocElements, new JavaConfigurationVariantMapping("runtime", true));
        }

        Configuration sourcesElements = feature.getSourcesElementsConfiguration();
        if (sourcesElements != null) {
            component.addVariantsFromConfiguration(sourcesElements, new JavaConfigurationVariantMapping("runtime", true));
        }

        if (allowPublication) {
            component.addVariantsFromConfiguration(feature.getApiElementsConfiguration(), new JavaConfigurationVariantMapping("compile", true));
            component.addVariantsFromConfiguration(feature.getRuntimeElementsConfiguration(), new JavaConfigurationVariantMapping("runtime", true));
        }
    }

}
