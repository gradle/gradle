/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal;

import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.DefaultGradle;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

public class TestBuildScopeServices extends BuildScopeServices {
    private final File homeDir;
    private final StartParameterInternal startParameter;

    public TestBuildScopeServices(ServiceRegistry parent, File homeDir, StartParameterInternal startParameter) {
        super(parent);
        this.homeDir = homeDir;
        this.startParameter = startParameter;
    }

    @Override
    protected GradleProperties createGradleProperties(GradlePropertiesController gradlePropertiesController) {
        return new EmptyGradleProperties();
    }

    protected BuildDefinition createBuildDefinition(StartParameterInternal startParameter) {
        return BuildDefinition.fromStartParameter(startParameter, null);
    }

    protected BuildCancellationToken createBuildCancellationToken() {
        return new DefaultBuildCancellationToken();
    }

    protected BuildClientMetaData createClientMetaData() {
        return new GradleLauncherMetaData();
    }

    protected CurrentGradleInstallation createCurrentGradleInstallation() {
        return new CurrentGradleInstallation(new GradleInstallation(homeDir));
    }

    protected GradleInternal createGradle(InstantiatorFactory instantiatorFactory, ServiceRegistryFactory serviceRegistryFactory) {
        return instantiatorFactory.decorateLenient().newInstance(DefaultGradle.class, null, startParameter, serviceRegistryFactory);
    }

    private static class EmptyGradleProperties implements GradleProperties {
        @Nullable
        @Override
        public String find(String propertyName) {
            return null;
        }

        @Override
        public Map<String, String> mergeProperties(Map<String, String> properties) {
            return properties;
        }
    }
}
