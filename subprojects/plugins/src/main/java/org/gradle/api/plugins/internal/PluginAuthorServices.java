/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.internal.DefaultJvmLanguageUtilities;
import org.gradle.api.plugins.jvm.internal.DefaultJvmPluginServices;
import org.gradle.api.publish.internal.component.DefaultSoftwareComponentFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;

/**
 * Registers services that can be used by plugin authors to develop their plugins.
 */
public class PluginAuthorServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeServices());
        registration.add(DefaultJvmLanguageUtilities.class);
        registration.add(DefaultJvmPluginServices.class);
    }

    private static class GlobalScopeServices {
        SoftwareComponentFactory createSoftwareComponentFactory(ObjectFactory objectFactory) {
            return new DefaultSoftwareComponentFactory(objectFactory);
        }
    }

    private static class ProjectScopeServices {
        SourceSetContainer createSourceSetContainer(ObjectFactory objectFactory) {
            return objectFactory.newInstance(DefaultSourceSetContainer.class);
        }
    }
}
