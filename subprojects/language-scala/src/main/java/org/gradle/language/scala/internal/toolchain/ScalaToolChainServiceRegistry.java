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

package org.gradle.language.scala.internal.toolchain;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

public class ScalaToolChainServiceRegistry extends AbstractPluginServiceRegistry {

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeCompileServices());
    }

    private static class ProjectScopeCompileServices {
        ScalaToolChainInternal createScalaToolChain(GradleInternal gradle, WorkerDaemonFactory workerDaemonFactory, ConfigurationContainer configurationContainer, DependencyHandler dependencyHandler, JavaForkOptionsFactory forkOptionsFactory, WorkerDirectoryProvider workerDirectoryProvider, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry, ActionExecutionSpecFactory actionExecutionSpecFactory, ClasspathHasher classpathHasher) {
            return new DownloadingScalaToolChain(gradle.getGradleUserHomeDir(), workerDirectoryProvider.getWorkingDirectory(), workerDaemonFactory, configurationContainer, dependencyHandler, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory, classpathHasher);
        }
    }
}
