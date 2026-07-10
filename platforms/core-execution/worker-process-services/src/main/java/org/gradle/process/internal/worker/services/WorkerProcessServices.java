/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal.worker.services;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class WorkerProcessServices extends AbstractGradleModuleServices {
    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new GradleUserHomeWorkerProcessServices());
    }

    private static class GradleUserHomeWorkerProcessServices implements ServiceRegistrationProvider {
        @Provides
        WorkerProcessFactory createWorkerProcessFactory(
            LoggingManagerInternal loggingManagerInternal,
            MessagingServer messagingServer,
            ClassPathRegistry classPathRegistry,
            TemporaryFileProvider temporaryFileProvider,
            JavaExecHandleFactory execHandleFactory,
            JvmVersionDetector jvmVersionDetector,
            MemoryManager memoryManager,
            GradleUserHomeDirProvider gradleUserHomeDirProvider,
            OutputEventListener outputEventListener
        ) {
            return new DefaultWorkerProcessFactory(
                loggingManagerInternal,
                messagingServer,
                classPathRegistry,
                new LongIdGenerator(),
                gradleUserHomeDirProvider.getGradleUserHomeDirectory(),
                temporaryFileProvider,
                execHandleFactory,
                jvmVersionDetector,
                outputEventListener,
                memoryManager
            );
        }

        @Provides
        WorkerProcessClassPathProvider createWorkerProcessClassPathProvider(GlobalScopedCacheBuilderFactory cacheBuilderFactory, ModuleRegistry moduleRegistry) {
            return new WorkerProcessClassPathProvider(cacheBuilderFactory, moduleRegistry);
        }
    }
}
