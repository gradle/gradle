/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.component.internal;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.ComponentFeature;
import org.gradle.api.component.ConfigurationBackedConsumableVariant;
import org.gradle.api.component.ConsumableVariant;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.DefaultAdhocSoftwareComponent;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.component.JvmSoftwareComponent;
import org.gradle.jvm.component.JvmFeature;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The software component created by the Java plugin.
 */
public class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponent {

    private final CompositeDomainObjectSet<ConsumableVariant> variants;
    private final ExtensiblePolymorphicDomainObjectContainer<ComponentFeature> features;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        Project project,
        Instantiator instantiator
    ) {
        super(componentName, instantiator);
        this.variants = CompositeDomainObjectSet.create(ConsumableVariant.class);
        this.features = project.getObjects().polymorphicDomainObjectContainer(ComponentFeature.class);

        // Map ConsumableVariant API to UsageContext API.
        variants.all(variant -> {
            if (variant instanceof ConfigurationBackedConsumableVariant) {

                Usage usage = variant.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE);
                String scope = usage != null && usage.getName().equals(Usage.JAVA_API) ? "compile" : "runtime";

                boolean optional = !variant.getCapabilities().getCapabilities().isEmpty();

                Configuration configuration = ((ConfigurationBackedConsumableVariant) variant).getConfiguration();
                addVariantsFromConfiguration(configuration, new JavaConfigurationVariantMapping(scope, optional));
            }
        });

        registerFeatureImplementations(project);

        // The current CompositeDomainObjectSet implementation is not lazy and eagerly realizes all
        // elements of child collections. We should fix this to allow component variants to be lazy.
        features.whenObjectAdded(feature -> this.variants.addCollection(feature.getVariants()));
        features.whenObjectRemoved(feature -> this.variants.removeCollection(feature.getVariants()));
    }

    /**
     * Registers general-purpose {@link ComponentFeature} implementations.
     */
    private void registerFeatureImplementations(Project project) {
        ObjectFactory objectFactory = project.getObjects();
        SourceSetContainer sourceSets = getJavaPluginExtension(project.getExtensions()).getSourceSets();

        features.registerFactory(JvmFeature.class, featureName -> {
            if (sourceSets.findByName(featureName) != null) {
                throw new GradleException("Cannot create JvmFeature since source set '" + featureName +"' already exists.");
            }

            List<Capability> capabilities = Collections.singletonList(new ProjectDerivedCapability(project, featureName));
            SourceSet sourceSet = sourceSets.create(featureName);

            return objectFactory.newInstance(DefaultJvmFeature.class,
                featureName, sourceSet, capabilities, "The '" + featureName + "' feature",
                project, ConfigurationRoles.INTENDED_CONSUMABLE, false
            );
        });
    }

    private static JavaPluginExtension getJavaPluginExtension(ExtensionContainer extensions) {
        JavaPluginExtension javaExtension = extensions.findByType(JavaPluginExtension.class);
        if (javaExtension == null) {
            throw new GradleException("The java-base plugin must be applied in order to create instances of " + DefaultJvmSoftwareComponent.class.getSimpleName() + ".");
        }
        return javaExtension;
    }


    public Set<? extends ConsumableVariant> getVariants() {
        return variants;
    }

    public ExtensiblePolymorphicDomainObjectContainer<ComponentFeature> getFeatures() {
        return features;
    }
}
