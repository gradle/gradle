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

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.event.ScopedListenerManager;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.GradleModuleServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.WorkerSharedBuildSessionScopeServices;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;

import java.util.List;

/**
 * Contains the services for a single build session, which could be a single build or multiple builds when in continuous mode.
 */
public class BuildSessionScopeServices extends WorkerSharedBuildSessionScopeServices implements ServiceRegistrationProvider {

    private final StartParameterInternal startParameter;
    private final BuildRequestMetaData buildRequestMetaData;
    private final ClassPath injectedPluginClassPath;
    private final BuildCancellationToken buildCancellationToken;
    private final BuildClientMetaData buildClientMetaData;
    private final BuildEventConsumer buildEventConsumer;

    public BuildSessionScopeServices(StartParameterInternal startParameter, BuildRequestMetaData buildRequestMetaData, ClassPath injectedPluginClassPath, BuildCancellationToken buildCancellationToken, BuildClientMetaData buildClientMetaData, BuildEventConsumer buildEventConsumer) {
        this.startParameter = startParameter;
        this.buildRequestMetaData = buildRequestMetaData;
        this.injectedPluginClassPath = injectedPluginClassPath;
        this.buildCancellationToken = buildCancellationToken;
        this.buildClientMetaData = buildClientMetaData;
        this.buildEventConsumer = buildEventConsumer;
    }

    void configure(ServiceRegistration registration, List<GradleModuleServices> servicesProviders) {
        registration.add(StartParameterInternal.class, startParameter);
        for (GradleModuleServices services : servicesProviders) {
            services.registerBuildSessionServices(registration);
        }
        registration.add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        registration.add(BuildCancellationToken.class, buildCancellationToken);
        registration.add(BuildRequestMetaData.class, buildRequestMetaData);
        registration.add(BuildClientMetaData.class, buildClientMetaData);
        registration.add(BuildEventConsumer.class, buildEventConsumer);
    }

    @Provides
    ScopedListenerManager createListenerManager(ScopedListenerManager parent) {
        return parent.createChild(Scope.BuildSession.class);
    }
}
