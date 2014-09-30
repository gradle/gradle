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
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.model.internal.inspect.MethodRuleDefinitionHandler;

public class ComponentModelBaseServiceRegistry implements PluginServiceRegistry {

    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration){
    }

    public void registerProjectServices(ServiceRegistration registration) {
            registration.addProvider(new ProjectScopeServices());
    }

    private static class ProjectScopeServices {
        MethodRuleDefinitionHandler createComponentModelPluginInspector(Instantiator instantiator) {
            return new ComponentTypeRuleDefinitionHandler(instantiator);
        }

        MethodRuleDefinitionHandler createBinaryTypeModelPluginInspector(Instantiator instantiator) {
            return new BinaryTypeRuleDefinitionHandler(instantiator);
        }

        MethodRuleDefinitionHandler createComponentBinariesPluginInspector() {
            return new ComponentBinariesRuleDefinitionHandler();
        }
        MethodRuleDefinitionHandler createBinaryTaskPluginInspector() {
            return new BinaryTasksRuleDefinitionHandler();
        }
    }
}
