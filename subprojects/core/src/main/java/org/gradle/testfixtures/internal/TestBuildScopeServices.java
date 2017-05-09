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
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
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
        return new DefaultBuildCancellationToken();
    }

    protected BuildClientMetaData createClientMetaData() {
        return new GradleLauncherMetaData();
    }

    protected CurrentGradleInstallation createCurrentGradleInstallation() {
        return new CurrentGradleInstallation(new GradleInstallation(homeDir));
    }

    protected NestedBuildFactory createNestedBuildFactory() {
        return new NestedBuildFactory() {
            @Override
            public GradleLauncher nestedInstance(StartParameter startParameter) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GradleLauncher nestedInstanceWithNewSession(StartParameter startParameter) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
