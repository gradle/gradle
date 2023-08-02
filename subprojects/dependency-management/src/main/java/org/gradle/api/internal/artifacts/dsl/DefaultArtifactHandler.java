/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.GUtil;

import java.util.Arrays;
import java.util.List;

public class DefaultArtifactHandler implements ArtifactHandler, MethodMixIn {

    private final ConfigurationContainer configurationContainer;
    private final NotationParser<Object, ConfigurablePublishArtifact> publishArtifactFactory;
    private final DynamicMethods dynamicMethods;

    public DefaultArtifactHandler(ConfigurationContainer configurationContainer, NotationParser<Object, ConfigurablePublishArtifact> publishArtifactFactory) {
        this.configurationContainer = configurationContainer;
        this.publishArtifactFactory = publishArtifactFactory;
        dynamicMethods = new DynamicMethods();
    }

    @SuppressWarnings("rawtypes")
    private PublishArtifact pushArtifact(org.gradle.api.artifacts.Configuration configuration, Object notation, Closure configureClosure) {
        Action<Object> configureAction = ConfigureUtil.configureUsing(configureClosure);
        return pushArtifact(configuration, notation, configureAction);
    }

    private PublishArtifact pushArtifact(Configuration configuration, Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
        warnIfConfigurationIsDeprecated((DeprecatableConfiguration) configuration);
        assertConfigurationIsValidForArtifacts((DeprecatableConfiguration)configuration);
        ConfigurablePublishArtifact publishArtifact = publishArtifactFactory.parseNotation(notation);
        configuration.getArtifacts().add(publishArtifact);
        configureAction.execute(publishArtifact);
        return publishArtifact;
    }

    // Update this with issue: https://github.com/gradle/gradle/issues/22339
    private void warnIfConfigurationIsDeprecated(DeprecatableConfiguration configuration) {
        // To avoid potentially adding new deprecation warnings in Gradle 8.0, we will maintain
        // the existing fully deprecated logic here (migrating the method out of DefaultConfiguration
        // so it isn't mistakenly used elsewhere)
        if (isFullyDeprecated(configuration)) {
            DeprecationLogger.deprecateConfiguration(configuration.getName()).forArtifactDeclaration()
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }

        // In Gradle 8.1, we'll update this check to only use the consumption deprecation, which is the only one
        // that it should need to check here
//        if (configuration.getConsumptionDeprecation() != null) {
//            DeprecationLogger.deprecateConfiguration(configuration.getName()).forArtifactDeclaration()
//                .willBecomeAnErrorInGradle9()
//                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
//                .nagUser();
//        }
    }

    /**
     * Exists only to maintain the existing fully deprecated logic in Gradle 8.0 - DO NOT USE.
     */
    private boolean isFullyDeprecated(DeprecatableConfiguration configuration) {
        return configuration.isDeprecatedForDeclarationAgainst() &&
            (!configuration.isCanBeConsumed() || configuration.isDeprecatedForConsumption()) &&
            (!configuration.isCanBeResolved() || configuration.isDeprecatedForResolution());
    }

    private void assertConfigurationIsValidForArtifacts(DeprecatableConfiguration configuration) {
        // And then in Gradle 9.0, this can finally become an error
//        if (!configuration.isCanBeConsumed()) {
//            throw new GradleException("Archives can not be added to the `" + configuration.getName() + "` configuration.");
//        }
    }

    @Override
    public PublishArtifact add(String configurationName, Object artifactNotation, Action<? super ConfigurablePublishArtifact> configureAction) {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, configureAction);
    }

    @Override
    public PublishArtifact add(String configurationName, Object artifactNotation) {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, Actions.doNothing());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PublishArtifact add(String configurationName, Object artifactNotation, Closure configureClosure) {
        return pushArtifact(configurationContainer.getByName(configurationName), artifactNotation, configureClosure);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    private class DynamicMethods implements MethodAccess {
        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return arguments.length > 0 && configurationContainer.findByName(name) != null;
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (arguments.length == 0) {
                return DynamicInvokeResult.notFound();
            }
            Configuration configuration = configurationContainer.findByName(name);
            if (configuration == null) {
                return DynamicInvokeResult.notFound();
            }
            List<Object> normalizedArgs = GUtil.flatten(Arrays.asList(arguments), false);
            if (normalizedArgs.size() == 2 && normalizedArgs.get(1) instanceof Closure) {
                return DynamicInvokeResult.found(pushArtifact(configuration, normalizedArgs.get(0), (Closure<?>) normalizedArgs.get(1)));
            } else {
                for (Object notation : normalizedArgs) {
                    pushArtifact(configuration, notation, Actions.doNothing());
                }
                return DynamicInvokeResult.found();
            }
        }
    }
}
