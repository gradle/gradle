/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;

public class ConnectionScopeServices extends DefaultServiceRegistry {
    public ConnectionScopeServices() {
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        add(LoggingServiceRegistry.class, loggingServices);
        add(new GlobalScopeServices(loggingServices));
    }

    protected ProviderConnection createProviderConnection() {
        return new ProviderConnection(
                get(LoggingServiceRegistry.class),
                get(GradleLauncherFactory.class),
                new PayloadSerializer(
                        new ModelClassLoaderRegistry()),
                new ActionClasspathFactory());
    }

    protected ProtocolToModelAdapter createProtocolToModelAdapter() {
        return new ProtocolToModelAdapter();
    }
}
