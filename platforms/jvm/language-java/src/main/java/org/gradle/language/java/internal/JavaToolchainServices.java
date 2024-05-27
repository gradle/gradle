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

package org.gradle.language.java.internal;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.process.internal.ExecHandleFactory;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

public class JavaToolchainServices extends AbstractGradleModuleServices {
    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeCompileServices());
    }

    private static class ProjectScopeCompileServices implements ServiceRegistrationProvider {
        @Provides
        JavaCompilerFactory createJavaCompilerFactory(
            WorkerDaemonFactory workerDaemonFactory,
            JavaForkOptionsFactory forkOptionsFactory,
            WorkerDirectoryProvider workerDirectoryProvider,
            ExecHandleFactory execHandleFactory,
            AnnotationProcessorDetector processorDetector,
            ClassPathRegistry classPathRegistry,
            ActionExecutionSpecFactory actionExecutionSpecFactory,
            InternalProblems problems,
            ProjectCacheDir projectCacheDir
        ) {
            return new DefaultJavaCompilerFactory(
                workerDirectoryProvider,
                workerDaemonFactory,
                forkOptionsFactory,
                execHandleFactory,
                processorDetector,
                classPathRegistry,
                actionExecutionSpecFactory,
                problems,
                projectCacheDir
            );
        }
    }
}
