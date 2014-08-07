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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.FixedBuildCancellationToken;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;

import java.io.File;

public class TestBuildScopeServices extends BuildScopeServices {
    private final File homeDir;

    public TestBuildScopeServices(ServiceRegistry parent, StartParameter startParameter, File homeDir) {
        super(parent, startParameter);
        this.homeDir = homeDir;
    }

    protected BuildCancellationToken createBuildCancellationToken() {
        return new FixedBuildCancellationToken();
    }

    protected BuildClientMetaData createClientMetaData() {
        return new GradleLauncherMetaData();
    }

    protected GradleDistributionLocator createGradleDistributionLocator() {
        return new GradleDistributionLocator() {
            public File getGradleHome() {
                return homeDir;
            }
        };
    }
}
