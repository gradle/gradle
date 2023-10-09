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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent;

import javax.inject.Inject;

/**
 * A component with a set of features. Each feature is responsible for compiling, executing, packaging, etc a software
 * product such as a library or application. This component owns all features it contains and therefore transitively owns their
 * corresponding source sets and any domain objects which are created by the {@link BasePlugin} on the source sets' behalf.
 * This includes their resolvable configurations and dependency scopes, as well as any associated tasks.
 *
 * TODO: We should strip almost all logic from this class. It should be a simple container for features and should provide
 * a means of querying all variants of all features.
 */
public abstract class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponentInternal {
    private final RoleBasedConfigurationContainerInternal configurations;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        ObjectFactory objectFactory,
        RoleBasedConfigurationContainerInternal configurations
    ) {
        super(componentName, objectFactory);
        this.configurations = configurations;
    }

    // TODO: The component itself should not be concerned with configuring the sources and javadoc jars
    // of its features. It should lazily react to the variants of the feature being added and configure
    // itself to in turn advertise those variants. However, this requires a more complete variant API,
    // which is still being designed. For now, we'll add the variants manually.

    @Override
    public void withJavadocJar() {
        getFeatures().configureEach(feature -> {
            feature.withJavadocJar();

            Configuration javadocElements = feature.getJavadocElementsConfiguration();
            if (!isRegisteredAsLegacyVariant(javadocElements)) {
                addVariantsFromConfiguration(javadocElements, new JavaConfigurationVariantMapping("runtime", true));
            }
        });
    }

    @Override
    public void withSourcesJar() {
        getFeatures().configureEach(feature -> {
            feature.withSourcesJar();

            Configuration sourcesElements = feature.getSourcesElementsConfiguration();
            if (!isRegisteredAsLegacyVariant(sourcesElements)) {
                addVariantsFromConfiguration(sourcesElements, new JavaConfigurationVariantMapping("runtime", true));
            }
        });
    }

    @Override
    public JvmFeatureInternal getMainFeature() {
        JvmFeatureInternal mainFeature = getFeatures().findByName(JvmConstants.JAVA_MAIN_FEATURE_NAME);
        if (mainFeature == null) {
            throw new IllegalStateException("Expected to find a feature named '" + JvmConstants.JAVA_MAIN_FEATURE_NAME + "' but found none.");
        }

        return mainFeature;
    }

    @Override
    public void useCompileClasspathConsistency() {
        getTestSuites().withType(JvmTestSuite.class).configureEach(testSuite -> {
            configurations.getByName(testSuite.getSources().getCompileClasspathConfigurationName())
                .shouldResolveConsistentlyWith(getMainFeature().getCompileClasspathConfiguration());
        });
    }

    @Override
    public void useRuntimeClasspathConsistency() {
        getTestSuites().withType(JvmTestSuite.class).configureEach(testSuite -> {
            configurations.getByName(testSuite.getSources().getRuntimeClasspathConfigurationName())
                .shouldResolveConsistentlyWith(getMainFeature().getRuntimeClasspathConfiguration());
        });
    }
}
