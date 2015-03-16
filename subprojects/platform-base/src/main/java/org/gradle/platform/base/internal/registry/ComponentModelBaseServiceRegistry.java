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

package org.gradle.platform.base.internal.registry;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.internal.toolchain.DefaultToolResolver;
import org.gradle.platform.base.internal.toolchain.ToolChainInternal;
import org.gradle.platform.base.internal.toolchain.ToolResolver;

public class ComponentModelBaseServiceRegistry implements PluginServiceRegistry {

    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    public void registerBuildServices(ServiceRegistration registration){
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeServices());
    }

    private static class ProjectScopeServices {
        ToolResolver createToolResolver(ServiceRegistry services) {
            DefaultToolResolver toolResolver = new DefaultToolResolver();
            for (ToolChainInternal<?> toolChain : services.getAll(ToolChainInternal.class)) {
                @SuppressWarnings("unchecked") ToolChainInternal<? extends Platform> converted = toolChain;
                toolResolver.registerToolChain(converted);
            }
            return toolResolver;
        }
    }

    private static class GlobalScopeServices {
        MethodModelRuleExtractor createLanguageTypePluginInspector() {
            return new LanguageTypeModelRuleExtractor();
        }

        MethodModelRuleExtractor createComponentModelPluginInspector(Instantiator instantiator) {
            return new ComponentTypeModelRuleExtractor(instantiator);
        }

        MethodModelRuleExtractor createBinaryTypeModelPluginInspector(Instantiator instantiator) {
            return new BinaryTypeModelRuleExtractor(instantiator);
        }

        MethodModelRuleExtractor createComponentBinariesPluginInspector() {
            return new ComponentBinariesModelRuleExtractor();
        }
        MethodModelRuleExtractor createBinaryTaskPluginInspector() {
            return new BinaryTasksModelRuleExtractor();
        }
    }
}
