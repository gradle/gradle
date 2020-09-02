/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.session;

import org.gradle.StartParameter;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.CrossBuildSessionScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;

import java.io.Closeable;

/**
 * Encapsulates the state for a build session.
 */
public class BuildSessionState implements Closeable {
    private final GradleUserHomeScopeServiceRegistry userHomeScopeServiceRegistry;
    private final ServiceRegistry userHomeServices;
    private final BuildSessionScopeServices sessionScopeServices;

    public BuildSessionState(GradleUserHomeScopeServiceRegistry userHomeScopeServiceRegistry,
                             CrossBuildSessionScopeServices crossBuildSessionServices,
                             StartParameter startParameter,
                             BuildRequestMetaData requestMetaData,
                             ClassPath injectedPluginClassPath,
                             BuildCancellationToken buildCancellationToken,
                             BuildClientMetaData buildClientMetaData,
                             BuildEventConsumer buildEventConsumer) {
        this.userHomeScopeServiceRegistry = userHomeScopeServiceRegistry;
        userHomeServices = userHomeScopeServiceRegistry.getServicesFor(startParameter.getGradleUserHomeDir());
        sessionScopeServices = new BuildSessionScopeServices(userHomeServices, crossBuildSessionServices, startParameter, requestMetaData, injectedPluginClassPath, buildCancellationToken, buildClientMetaData, buildEventConsumer);
    }

    public ServiceRegistry getServices() {
        return sessionScopeServices;
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(sessionScopeServices, (Closeable) () -> userHomeScopeServiceRegistry.release(userHomeServices)).stop();
    }
}
