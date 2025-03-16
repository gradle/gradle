/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.internal.tooling;

import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction;

public class ToolingNativeServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(BuildScopeToolingModelBuilderRegistryAction.class, ToolingModelRegistration.class);
    }

    public static class ToolingModelRegistration implements BuildScopeToolingModelBuilderRegistryAction {
        @Override
        public void execute(ToolingModelBuilderRegistry registry) {
            registry.register(new CppModelBuilder());
        }
    }
}
