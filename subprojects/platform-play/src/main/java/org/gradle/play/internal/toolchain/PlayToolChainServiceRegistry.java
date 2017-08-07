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

package org.gradle.play.internal.toolchain;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.play.internal.spec.PlayApplicationBinaryRenderer;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.WorkerDaemonFactory;

public class PlayToolChainServiceRegistry extends AbstractPluginServiceRegistry {

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(PlayApplicationBinaryRenderer.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeCompileServices());
    }

    private static class ProjectScopeCompileServices {
        PlayToolChainInternal createPlayToolChain(FileResolver fileResolver, WorkerDaemonFactory workerDaemonFactory, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, WorkerProcessFactory workerProcessBuilderFactory, WorkerDirectoryProvider workerDirectoryProvider, BuildGateToken buildGate) {
            return new DefaultPlayToolChain(fileResolver, workerDaemonFactory, configurationContainer, dependencyHandler, workerProcessBuilderFactory, workerDirectoryProvider, buildGate);
        }
    }
}
