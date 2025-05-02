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

import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class TestBuildScopeServices extends BuildScopeServices {
    private final File homeDir;

    public TestBuildScopeServices(File homeDir, BuildModelControllerServices.Supplier supplier) {
        super(supplier);
        this.homeDir = homeDir;
    }

    @Provides
    protected DefaultProjectDescriptorRegistry createProjectDescriptorRegistry() {
        return new DefaultProjectDescriptorRegistry();
    }

    @Override
    @Provides
    protected GradleProperties createGradleProperties(GradlePropertiesController gradlePropertiesController) {
        return new EmptyGradleProperties();
    }

    @Provides
    protected BuildCancellationToken createBuildCancellationToken() {
        return new DefaultBuildCancellationToken();
    }

    @Provides
    protected CurrentGradleInstallation createCurrentGradleInstallation() {
        return new CurrentGradleInstallation(new GradleInstallation(homeDir));
    }

    private static class EmptyGradleProperties implements GradleProperties {
        @Nullable
        @Override
        public Object find(String propertyName) {
            return null;
        }

        @Override
        public Map<String, Object> mergeProperties(Map<String, Object> properties) {
            return properties;
        }

        @Override
        public Map<String, Object> getProperties() {
            return Collections.emptyMap();
        }
    }
}
