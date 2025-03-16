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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class ToolingBuilderServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(BuildEventListenerFactory.class, ToolingApiBuildEventListenerFactory.class);
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.add(BuildControllerFactory.class);
        registration.add(BuildActionRunner.class, BuildModelActionRunner.class);
        registration.add(BuildActionRunner.class, TestExecutionRequestActionRunner.class);
        registration.add(BuildActionRunner.class, ClientProvidedBuildActionRunner.class);
        registration.add(BuildActionRunner.class, ClientProvidedPhasedActionRunner.class);
    }
}
