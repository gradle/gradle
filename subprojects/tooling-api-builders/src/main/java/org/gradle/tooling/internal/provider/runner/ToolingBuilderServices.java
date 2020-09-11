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
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.launcher.exec.ChainingBuildActionRunner;

import java.util.Arrays;
import java.util.List;

public class ToolingBuilderServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            BuildActionRunner createBuildActionRunner(final BuildOperationListenerManager buildOperationListenerManager) {
                return new ChainingBuildActionRunner(
                    Arrays.asList(
                        new BuildModelActionRunner(),
                        new TestExecutionRequestActionRunner(buildOperationListenerManager),
                        new ClientProvidedBuildActionRunner(),
                        new ClientProvidedPhasedActionRunner()));
            }

            BuildEventListenerFactory createToolingApiSubscribableBuildActionRunnerRegistration(BuildOperationIdFactory buildOperationIdFactory, List<OperationResultPostProcessorFactory> postProcessorFactories) {
                return new ToolingApiBuildEventListenerFactory(buildOperationIdFactory, postProcessorFactories);
            }
        });
    }
}
