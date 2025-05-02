/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resource.transport.sftp;


import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class SftpResourcesServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    private static class GlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        SftpClientFactory createSftpClientFactory() {
            return new SftpClientFactory();
        }

        @Provides
        ResourceConnectorFactory createSftpConnectorFactory(SftpClientFactory clientFactory) {
            return new SftpConnectorFactory(clientFactory);
        }
    }
}
