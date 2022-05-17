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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.upgrade.ApiUpgradeManager;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

public class GroovyServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new ProviderApiMigrationAction());
    }

    private static class ProjectServices {
        public GroovyCompilerFactory createGroovyCompilerFactory(
            WorkerDaemonFactory workerDaemonFactory,
            IsolatedClassloaderWorkerFactory inProcessWorkerFactory,
            JavaForkOptionsFactory forkOptionsFactory,
            AnnotationProcessorDetector processorDetector,
            JvmVersionDetector jvmVersionDetector,
            WorkerDirectoryProvider workerDirectoryProvider,
            ClassPathRegistry classPathRegistry,
            ClassLoaderRegistry classLoaderRegistry,
            ActionExecutionSpecFactory actionExecutionSpecFactory
        ) {
            return new GroovyCompilerFactory(
                workerDaemonFactory,
                inProcessWorkerFactory,
                forkOptionsFactory,
                processorDetector,
                jvmVersionDetector,
                workerDirectoryProvider,
                classPathRegistry,
                classLoaderRegistry,
                actionExecutionSpecFactory
            );
        }
    }

    private static class ProviderApiMigrationAction {
        public void configure(ApiUpgradeManager upgradeManager) {
            upgradeManager
                .matchProperty(GroovyCompile.class, String.class, "targetCompatibility")
                .replaceWith(
                    abstractCompile -> abstractCompile.getTargetCompatibility().get(),
                    (abstractCompile, value) -> abstractCompile.getTargetCompatibility().set(value)
                );
            upgradeManager
                .matchProperty(GroovyCompile.class, String.class, "sourceCompatibility")
                .replaceWith(
                    abstractCompile -> abstractCompile.getSourceCompatibility().get(),
                    (abstractCompile, value) -> abstractCompile.getSourceCompatibility().set(value)
                );
        }
    }
}
